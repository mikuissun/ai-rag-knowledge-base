package com.mikuissun.knowledgebase.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.dto.DocumentListResponse;
import com.mikuissun.knowledgebase.document.dto.DocumentResponse;
import com.mikuissun.knowledgebase.document.entity.Document;
import com.mikuissun.knowledgebase.document.mapper.DocumentMapper;
import com.mikuissun.knowledgebase.document.parser.DocumentParserFactory;
import com.mikuissun.knowledgebase.knowledge.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class DocumentServiceImpl implements DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);
    private final DocumentMapper mapper;
    private final KnowledgeBaseService knowledgeBases;
    private final DocumentParserFactory parsers;
    private final LocalDocumentStorage storage;
    private final TransactionTemplate transactions;

    public DocumentServiceImpl(DocumentMapper mapper, KnowledgeBaseService knowledgeBases,
            DocumentParserFactory parsers, LocalDocumentStorage storage, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.knowledgeBases = knowledgeBases;
        this.parsers = parsers;
        this.storage = storage;
        this.transactions = new TransactionTemplate(manager);
        // Commit happens inside execute(), so filesystem compensation also covers commit failures.
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public DocumentResponse upload(Long knowledgeBaseId, Long userId, MultipartFile file) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        if (file == null || file.isEmpty()) throw new BusinessException(400, "文件不能为空");
        if (file.getSize() > LocalDocumentStorage.MAX_FILE_SIZE) throw new BusinessException(413, "文件不能超过 20MB");
        String name = safeName(file.getOriginalFilename());
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        var parser = parsers.getParser(extension);
        validateContentType(extension, file.getContentType());
        String relative = storage.newRelativePath(userId, knowledgeBaseId, extension);
        try {
            Path path = storage.save(relative, file);
            String content;
            try {
                content = parser.parse(path);
                if (content.isBlank()) throw new IOException("No extractable text");
            } catch (IOException | RuntimeException exception) {
                throw new BusinessException(400, "无法解析文档：请检查格式、UTF-8 编码、加密或扫描件；正文上限为 500 万字符");
            }
            Document document = new Document();
            document.setKnowledgeBaseId(knowledgeBaseId);
            document.setUserId(userId);
            document.setOriginalName(name);
            document.setStoredName(path.getFileName().toString());
            document.setFilePath(relative);
            document.setFileType(extension);
            document.setFileSize(Files.size(path));
            document.setContentText(content);
            document.setStatus(1);
            return transactions.execute(transaction -> {
                knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
                if (mapper.insert(document) != 1) throw new IllegalStateException("Document insert failed");
                return DocumentResponse.from(find(knowledgeBaseId, document.getId(), userId, false));
            });
        } catch (IOException exception) {
            cleanup(relative);
            throw new BusinessException(500, "文件存储失败");
        } catch (RuntimeException exception) {
            cleanup(relative);
            throw exception;
        }
    }

    @Override
    public List<DocumentListResponse> list(Long knowledgeBaseId, Long userId) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        return mapper.selectList(new LambdaQueryWrapper<Document>()
                .select(Document::getId, Document::getKnowledgeBaseId, Document::getOriginalName,
                        Document::getFileType, Document::getFileSize, Document::getStatus,
                        Document::getCreatedAt, Document::getUpdatedAt, Document::getProcessingStatus,
                        Document::getProcessingError, Document::getProcessedAt)
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId).eq(Document::getUserId, userId)
                .orderByDesc(Document::getCreatedAt, Document::getId))
                .stream().map(DocumentListResponse::from).toList();
    }

    @Override
    public DocumentResponse get(Long knowledgeBaseId, Long documentId, Long userId) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        return DocumentResponse.from(find(knowledgeBaseId, documentId, userId, false));
    }

    @Override
    public void delete(Long knowledgeBaseId, Long documentId, Long userId) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        Path[] moved = new Path[2];
        try {
            transactions.executeWithoutResult(transaction -> {
                Document document = find(knowledgeBaseId, documentId, userId, true);
                try {
                    Path original = storage.resolve(document.getFilePath());
                    if (Files.exists(original)) {
                        Path pending = storage.resolve(document.getFilePath() + "." + UUID.randomUUID() + ".deleting");
                        Files.move(original, pending);
                        moved[0] = original;
                        moved[1] = pending;
                    } else {
                        log.warn("Original file missing for document {}", documentId);
                    }
                } catch (IOException exception) {
                    throw new BusinessException(500, "文件删除失败，数据库记录已保留");
                }
                if (mapper.delete(new LambdaQueryWrapper<Document>()
                        .eq(Document::getId, documentId).eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(Document::getUserId, userId)) != 1) {
                    throw new IllegalStateException("Document delete failed");
                }
            });
        } catch (RuntimeException exception) {
            if (moved[1] != null) {
                try {
                    Files.move(moved[1], moved[0]);
                } catch (IOException restoreFailure) {
                    log.error("Restore failed for document {}; retain .deleting file for recovery", documentId, restoreFailure);
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw exception;
        }
        if (moved[1] != null) {
            try {
                Files.deleteIfExists(moved[1]);
            } catch (IOException exception) {
                log.error("Database deletion committed but pending file needs cleanup for document {}", documentId, exception);
                throw new BusinessException(500, "数据库记录已删除，但文件清理失败，请联系管理员");
            }
        }
    }

    private Document find(Long knowledgeBaseId, Long documentId, Long userId, boolean lock) {
        var query = new LambdaQueryWrapper<Document>().eq(Document::getId, documentId)
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId).eq(Document::getUserId, userId);
        if (lock) query.last("FOR UPDATE");
        Document document = mapper.selectOne(query);
        if (document == null) throw new BusinessException(404, "文档不存在");
        return document;
    }

    private String safeName(String original) {
        if (original == null) throw new BusinessException(400, "文件名不能为空");
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255 || name.lastIndexOf('.') <= 0 || name.startsWith(".") || name.endsWith(".")
                || name.chars().anyMatch(c -> Character.isISOControl(c) || c == ':' || c == '<'
                || c == '>' || c == '"' || c == '|' || c == '?' || c == '*')) {
            throw new BusinessException(400, "文件名不合法");
        }
        return name;
    }

    private void validateContentType(String extension, String type) {
        if (type == null || type.isBlank()) return;
        String mime = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("application/octet-stream".equals(mime)) return;
        boolean allowed = switch (extension) {
            case "pdf" -> mime.equals("application/pdf");
            case "docx" -> mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "md" -> Set.of("text/markdown", "text/x-markdown", "text/plain").contains(mime);
            case "txt" -> mime.equals("text/plain");
            default -> false;
        };
        if (!allowed) throw new BusinessException(400, "Content-Type 与文件扩展名不匹配");
    }

    private void cleanup(String relative) {
        try {
            Files.deleteIfExists(storage.resolve(relative));
        } catch (IOException exception) {
            log.error("Upload compensation failed; orphan file requires cleanup: {}", relative, exception);
        }
    }
}
