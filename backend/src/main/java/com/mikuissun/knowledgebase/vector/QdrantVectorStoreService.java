package com.mikuissun.knowledgebase.vector;

import com.google.common.util.concurrent.ListenableFuture;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.qdrant.client.ConditionFactory.match;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

@Service
public class QdrantVectorStoreService implements VectorStoreService {
    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStoreService.class);
    private final QdrantClient client;
    private final QdrantProperties properties;
    private volatile boolean collectionReady;

    public QdrantVectorStoreService(QdrantClient client, QdrantProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void ensureCollection() {
        if (collectionReady) return;
        synchronized (this) {
            if (collectionReady) return;
            Duration timeout = timeout();
            boolean exists = await(client.collectionExistsAsync(properties.getCollection(), timeout),
                    "检查 Qdrant collection");
            if (!exists) {
                Collections.VectorParams params = Collections.VectorParams.newBuilder()
                        .setSize(properties.getDimension())
                        .setDistance(Collections.Distance.Cosine)
                        .build();
                await(client.createCollectionAsync(properties.getCollection(), params, timeout),
                        "创建 Qdrant collection");
            }
            validateCollection(await(client.getCollectionInfoAsync(properties.getCollection(), timeout),
                    "读取 Qdrant collection 配置"));
            collectionReady = true;
        }
    }

    @Override
    public void replaceDocumentPoints(Long userId, Long knowledgeBaseId, Long documentId,
                                      List<VectorPoint> points) {
        validateIds(userId, knowledgeBaseId, documentId);
        if (points == null || points.isEmpty()) {
            throw new BusinessException(400, "没有可写入 Qdrant 的文本块");
        }
        points.forEach(point -> validatePoint(point, userId, knowledgeBaseId, documentId));
        ensureCollection();
        deleteByFilter(documentFilter(userId, knowledgeBaseId, documentId));
        for (int start = 0; start < points.size(); start += properties.getBatchSize()) {
            int end = Math.min(start + properties.getBatchSize(), points.size());
            List<Points.PointStruct> batch = points.subList(start, end).stream()
                    .map(this::toPoint).toList();
            Points.UpsertPoints request = Points.UpsertPoints.newBuilder()
                    .setCollectionName(properties.getCollection())
                    .setWait(true)
                    .addAllPoints(batch)
                    .build();
            await(client.upsertAsync(request, timeout()), "写入 Qdrant 向量");
        }
    }

    @Override
    public void deleteDocumentPoints(Long userId, Long knowledgeBaseId, Long documentId) {
        validateIds(userId, knowledgeBaseId, documentId);
        ensureCollection();
        deleteByFilter(documentFilter(userId, knowledgeBaseId, documentId));
    }

    @Override
    public void deleteKnowledgeBasePoints(Long userId, Long knowledgeBaseId) {
        if (userId == null || knowledgeBaseId == null) throw new IllegalArgumentException("IDs must not be null");
        ensureCollection();
        deleteByFilter(tenantFilter(userId, knowledgeBaseId));
    }

    @Override
    public List<VectorSearchResult> similaritySearch(Long userId, Long knowledgeBaseId,
                                                     float[] queryVector, int topK) {
        if (userId == null || knowledgeBaseId == null) throw new IllegalArgumentException("IDs must not be null");
        validateVector(queryVector);
        if (topK < 1 || topK > properties.getMaxSearchTopK()) {
            throw new BusinessException(400, "topK 必须在 1 到 " + properties.getMaxSearchTopK() + " 之间");
        }
        ensureCollection();
        Points.SearchPoints request = Points.SearchPoints.newBuilder()
                .setCollectionName(properties.getCollection())
                .addAllVector(toFloatList(queryVector))
                .setFilter(tenantFilter(userId, knowledgeBaseId))
                .setLimit(topK)
                .setWithPayload(enable(true))
                .build();
        return await(client.searchAsync(request, timeout()), "执行 Qdrant 相似度检索")
                .stream().map(this::toSearchResult).toList();
    }

    @Override
    public String collectionName() {
        return properties.getCollection();
    }

    @Override
    public int dimension() {
        return properties.getDimension();
    }

    private void validateCollection(Collections.CollectionInfo info) {
        Collections.VectorsConfig vectorsConfig = info.getConfig().getParams().getVectorsConfig();
        if (!vectorsConfig.hasParams()) {
            throw new BusinessException(409, "Qdrant collection 不是单一稠密向量配置");
        }
        Collections.VectorParams params = vectorsConfig.getParams();
        if (params.getSize() != properties.getDimension() || params.getDistance() != Collections.Distance.Cosine) {
            throw new BusinessException(409, "Qdrant collection 维度或距离算法与应用配置不兼容");
        }
    }

    private Points.PointStruct toPoint(VectorPoint point) {
        return Points.PointStruct.newBuilder()
                .setId(id(point.chunkId()))
                .setVectors(vectors(point.vector()))
                .putAllPayload(Map.of(
                        "chunkId", value(point.chunkId()),
                        "documentId", value(point.documentId()),
                        "knowledgeBaseId", value(point.knowledgeBaseId()),
                        "userId", value(point.userId()),
                        "chunkIndex", value(point.chunkIndex().longValue()),
                        "content", value(point.content())))
                .build();
    }

    private VectorSearchResult toSearchResult(Points.ScoredPoint point) {
        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
        return new VectorSearchResult(integer(payload, "chunkId"), integer(payload, "documentId"),
                Math.toIntExact(integer(payload, "chunkIndex")), string(payload, "content"), point.getScore());
    }

    private long integer(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null || value.getKindCase() != JsonWithInt.Value.KindCase.INTEGER_VALUE) {
            throw new BusinessException(502, "Qdrant payload 缺少字段：" + key);
        }
        return value.getIntegerValue();
    }

    private String string(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null || value.getKindCase() != JsonWithInt.Value.KindCase.STRING_VALUE) {
            throw new BusinessException(502, "Qdrant payload 缺少字段：" + key);
        }
        return value.getStringValue();
    }

    private void deleteByFilter(Points.Filter filter) {
        Points.DeletePoints request = Points.DeletePoints.newBuilder()
                .setCollectionName(properties.getCollection())
                .setWait(true)
                .setPoints(Points.PointsSelector.newBuilder().setFilter(filter))
                .build();
        await(client.deleteAsync(request, timeout()), "删除 Qdrant 向量");
    }

    private Points.Filter tenantFilter(Long userId, Long knowledgeBaseId) {
        return Points.Filter.newBuilder()
                .addMust(match("userId", userId))
                .addMust(match("knowledgeBaseId", knowledgeBaseId))
                .build();
    }

    private Points.Filter documentFilter(Long userId, Long knowledgeBaseId, Long documentId) {
        return tenantFilter(userId, knowledgeBaseId).toBuilder()
                .addMust(match("documentId", documentId))
                .build();
    }

    private void validatePoint(VectorPoint point, Long userId, Long knowledgeBaseId, Long documentId) {
        if (point == null || point.chunkId() == null || point.chunkIndex() == null
                || point.content() == null || point.content().isBlank()
                || !userId.equals(point.userId()) || !knowledgeBaseId.equals(point.knowledgeBaseId())
                || !documentId.equals(point.documentId())) {
            throw new BusinessException(400, "Qdrant Point 数据不完整或归属不匹配");
        }
        validateVector(point.vector());
    }

    private void validateVector(float[] vector) {
        if (vector == null || vector.length != properties.getDimension()) {
            throw new BusinessException(400, "向量维度必须为 " + properties.getDimension());
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new BusinessException(400, "向量包含非法数值");
        }
    }

    private void validateIds(Long userId, Long knowledgeBaseId, Long documentId) {
        if (userId == null || knowledgeBaseId == null || documentId == null) {
            throw new IllegalArgumentException("IDs must not be null");
        }
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) values.add(value);
        return values;
    }

    private Duration timeout() {
        return Duration.ofSeconds(properties.getTimeoutSeconds());
    }

    private <T> T await(ListenableFuture<T> future, String operation) {
        try {
            return future.get(properties.getTimeoutSeconds() + 1L, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new BusinessException(504, operation + "超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, operation + "被中断");
        } catch (ExecutionException exception) {
            log.warn("{}失败: {}", operation, exception.getCause() == null
                    ? exception.getClass().getSimpleName() : exception.getCause().getClass().getSimpleName());
            throw new BusinessException(503, operation + "失败，请确认 Qdrant 已启动且配置正确");
        }
    }
}
