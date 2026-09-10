package com.mikuissun.knowledgebase.document.embedding;

import java.util.List;

public interface EmbeddingService {
    List<float[]> embedBatch(List<String> texts);
    String modelName();
    int dimension();
}
