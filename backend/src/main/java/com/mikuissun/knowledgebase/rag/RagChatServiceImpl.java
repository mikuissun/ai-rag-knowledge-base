package com.mikuissun.knowledgebase.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.embedding.EmbeddingService;
import com.mikuissun.knowledgebase.document.entity.Document;
import com.mikuissun.knowledgebase.document.mapper.DocumentMapper;
import com.mikuissun.knowledgebase.knowledge.service.KnowledgeBaseService;
import com.mikuissun.knowledgebase.llm.ChatModelService;
import com.mikuissun.knowledgebase.rag.dto.CitationResponse;
import com.mikuissun.knowledgebase.rag.dto.RagChatRequest;
import com.mikuissun.knowledgebase.rag.dto.RagChatResponse;
import com.mikuissun.knowledgebase.rag.dto.SseDone;
import com.mikuissun.knowledgebase.rag.dto.SseError;
import com.mikuissun.knowledgebase.rag.dto.SseMessage;
import com.mikuissun.knowledgebase.rag.prompt.RagPrompt;
import com.mikuissun.knowledgebase.rag.prompt.RagPromptBuilder;
import com.mikuissun.knowledgebase.rag.prompt.RagPromptSource;
import com.mikuissun.knowledgebase.vector.QdrantProperties;
import com.mikuissun.knowledgebase.vector.VectorSearchResult;
import com.mikuissun.knowledgebase.vector.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RagChatServiceImpl implements RagChatService {
    static final String NO_CONTEXT_ANSWER = "根据当前知识库内容，没有找到足够相关的信息。";
    private static final int CITATION_SNIPPET_CHARS = 240;
    private static final Logger log = LoggerFactory.getLogger(RagChatServiceImpl.class);

    private final KnowledgeBaseService knowledgeBases;
    private final EmbeddingService embeddings;
    private final VectorStoreService vectorStore;
    private final DocumentMapper documents;
    private final ChatModelService chatModel;
    private final RagPromptBuilder promptBuilder;
    private final QdrantProperties qdrantProperties;
    private final RagChatProperties ragProperties;
    private final ThreadPoolTaskExecutor executor;

    public RagChatServiceImpl(KnowledgeBaseService knowledgeBases, EmbeddingService embeddings,
            VectorStoreService vectorStore, DocumentMapper documents, ChatModelService chatModel,
            RagPromptBuilder promptBuilder, QdrantProperties qdrantProperties,
            RagChatProperties ragProperties,
            @Qualifier("ragSseExecutor") ThreadPoolTaskExecutor executor) {
        this.knowledgeBases = knowledgeBases;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.documents = documents;
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.qdrantProperties = qdrantProperties;
        this.ragProperties = ragProperties;
        this.executor = executor;
    }

    @Override
    public RagChatResponse chat(Long knowledgeBaseId, Long userId, RagChatRequest request) {
        PreparedRag prepared = prepare(knowledgeBaseId, userId, request);
        if (prepared.prompt() == null) return new RagChatResponse(NO_CONTEXT_ANSWER, List.of());
        String answer = chatModel.chat(prepared.prompt().systemPrompt(), prepared.prompt().userPrompt());
        if (answer == null || answer.isBlank()) throw new BusinessException(502, "LLM 返回了空答案");
        return new RagChatResponse(answer.trim(), prepared.citations());
    }

    @Override
    public SseEmitter stream(Long knowledgeBaseId, Long userId, RagChatRequest request) {
        PreparedRag prepared = prepare(knowledgeBaseId, userId, request);
        SseEmitter emitter = new SseEmitter(ragProperties.getSseTimeoutSeconds() * 1000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean serverCompleting = new AtomicBoolean(false);
        AtomicReference<Future<?>> task = new AtomicReference<>();

        emitter.onTimeout(() -> cancelClientTask(closed, serverCompleting, task));
        emitter.onError(exception -> cancelClientTask(closed, serverCompleting, task));
        emitter.onCompletion(() -> cancelClientTask(closed, serverCompleting, task));
        try {
            task.set(executor.submit(() -> streamPrepared(prepared, emitter, closed, serverCompleting)));
        } catch (TaskRejectedException exception) {
            throw new BusinessException(503, "流式问答服务繁忙，请稍后重试");
        }
        return emitter;
    }

    private PreparedRag prepare(Long knowledgeBaseId, Long userId, RagChatRequest request) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        String question = request.question().trim();
        int topK = request.topK() == null ? qdrantProperties.getSearchTopK() : request.topK();
        if (topK < 1 || topK > qdrantProperties.getMaxSearchTopK()) {
            throw new BusinessException(400,
                    "topK 必须在 1 到 " + qdrantProperties.getMaxSearchTopK() + " 之间");
        }

        List<float[]> queryEmbeddings = embeddings.embedBatch(List.of(question));
        if (queryEmbeddings == null || queryEmbeddings.size() != 1 || queryEmbeddings.get(0) == null
                || queryEmbeddings.get(0).length != vectorStore.dimension()) {
            throw new BusinessException(502, "查询 Embedding 返回数量或维度不正确");
        }
        List<VectorSearchResult> rawResults = vectorStore.similaritySearch(
                userId, knowledgeBaseId, queryEmbeddings.get(0), topK);
        List<VectorSearchResult> qualified = qualifiedResults(rawResults);
        if (qualified.isEmpty()) return new PreparedRag(null, List.of());

        Set<Long> documentIds = new LinkedHashSet<>();
        qualified.forEach(result -> documentIds.add(result.documentId()));
        Map<Long, String> names = new LinkedHashMap<>();
        documents.selectList(new LambdaQueryWrapper<Document>()
                        .select(Document::getId, Document::getOriginalName)
                        .eq(Document::getUserId, userId)
                        .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                        .in(Document::getId, documentIds))
                .forEach(document -> names.put(document.getId(), document.getOriginalName()));

        List<SelectedSource> selected = selectContext(qualified, names);
        if (selected.isEmpty()) return new PreparedRag(null, List.of());
        List<RagPromptSource> promptSources = selected.stream()
                .map(source -> new RagPromptSource(source.documentName(), source.result().documentId(),
                        source.result().chunkIndex(), source.contextContent()))
                .toList();
        List<CitationResponse> citations = selected.stream()
                .map(source -> new CitationResponse(source.result().documentId(), source.documentName(),
                        source.result().chunkId(), source.result().chunkIndex(), source.result().score(),
                        snippet(source.contextContent())))
                .toList();
        return new PreparedRag(promptBuilder.build(question, promptSources), citations);
    }

    private List<VectorSearchResult> qualifiedResults(List<VectorSearchResult> rawResults) {
        if (rawResults == null || rawResults.isEmpty()) return List.of();
        Map<Long, VectorSearchResult> unique = new LinkedHashMap<>();
        rawResults.stream()
                .filter(result -> result != null && result.chunkId() != null && result.documentId() != null
                        && result.chunkIndex() != null && result.content() != null
                        && !result.content().isBlank() && Float.isFinite(result.score())
                        && result.score() >= ragProperties.getMinScore())
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .forEach(result -> unique.putIfAbsent(result.chunkId(), result));
        return List.copyOf(unique.values());
    }

    private List<SelectedSource> selectContext(List<VectorSearchResult> results, Map<Long, String> names) {
        List<SelectedSource> selected = new ArrayList<>();
        int remaining = ragProperties.getMaxContextChars();
        for (VectorSearchResult result : results) {
            String documentName = names.get(result.documentId());
            if (documentName == null || remaining <= 0) continue;
            String content = result.content().strip();
            if (content.length() <= remaining) {
                selected.add(new SelectedSource(result, documentName, content));
                remaining -= content.length();
            } else if (selected.isEmpty()) {
                String truncated = truncateNaturally(content, remaining);
                if (!truncated.isBlank()) selected.add(new SelectedSource(result, documentName, truncated));
                break;
            } else {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private String truncateNaturally(String content, int limit) {
        if (content.length() <= limit) return content;
        int target = Math.max(1, limit - 1);
        int minimumBoundary = Math.max(0, target * 2 / 3);
        int cut = target;
        for (int index = target - 1; index >= minimumBoundary; index--) {
            char value = content.charAt(index);
            if (value == '\n' || "。！？；.!?; ".indexOf(value) >= 0) {
                cut = index + 1;
                break;
            }
        }
        return content.substring(0, cut).stripTrailing() + "…";
    }

    private String snippet(String content) {
        String normalized = content.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= CITATION_SNIPPET_CHARS) return normalized;
        return normalized.substring(0, CITATION_SNIPPET_CHARS - 1).stripTrailing() + "…";
    }

    private void streamPrepared(PreparedRag prepared, SseEmitter emitter,
                                AtomicBoolean closed, AtomicBoolean serverCompleting) {
        try {
            if (prepared.prompt() == null) {
                send(emitter, closed, "message", new SseMessage(NO_CONTEXT_ANSWER));
            } else {
                chatModel.streamChat(prepared.prompt().systemPrompt(), prepared.prompt().userPrompt(),
                        delta -> sendUnchecked(emitter, closed, delta));
            }
            send(emitter, closed, "sources", prepared.citations());
            send(emitter, closed, "done", new SseDone(true));
            complete(emitter, closed, serverCompleting);
        } catch (ClientDisconnectedException exception) {
            complete(emitter, closed, serverCompleting);
        } catch (RuntimeException exception) {
            String message = exception instanceof BusinessException business
                    ? business.getMessage() : "流式问答处理失败";
            log.warn("RAG streaming request failed: {}", message);
            try {
                send(emitter, closed, "error", new SseError(message));
            } catch (IOException ignored) {
                log.debug("SSE client disconnected before the error event was sent");
            }
            complete(emitter, closed, serverCompleting);
        } catch (IOException exception) {
            complete(emitter, closed, serverCompleting);
        }
    }

    private void sendUnchecked(SseEmitter emitter, AtomicBoolean closed, String delta) {
        try {
            send(emitter, closed, "message", new SseMessage(delta));
        } catch (IOException exception) {
            throw new ClientDisconnectedException(exception);
        }
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, String event, Object data) throws IOException {
        if (closed.get() || Thread.currentThread().isInterrupted()) {
            throw new ClientDisconnectedException(null);
        }
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private void cancelClientTask(AtomicBoolean closed, AtomicBoolean serverCompleting,
                                  AtomicReference<Future<?>> task) {
        closed.set(true);
        if (!serverCompleting.get()) {
            Future<?> future = task.get();
            if (future != null && !future.isDone()) future.cancel(true);
        }
    }

    private void complete(SseEmitter emitter, AtomicBoolean closed, AtomicBoolean serverCompleting) {
        if (closed.compareAndSet(false, true)) {
            serverCompleting.set(true);
            emitter.complete();
        }
    }

    private record SelectedSource(VectorSearchResult result, String documentName, String contextContent) { }
    private record PreparedRag(RagPrompt prompt, List<CitationResponse> citations) { }

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) { super(cause); }
    }
}
