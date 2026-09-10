ALTER TABLE documents
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER status,
    ADD COLUMN processing_error VARCHAR(1000) NULL AFTER processing_status,
    ADD COLUMN processed_at DATETIME NULL AFTER processing_error,
    ADD UNIQUE INDEX uk_documents_identity (id, user_id, knowledge_base_id);

CREATE TABLE document_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content LONGTEXT NOT NULL,
    char_count INT NOT NULL,
    token_estimate INT NOT NULL,
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_document_chunks_document_id (document_id),
    INDEX idx_document_chunks_knowledge_base_id (knowledge_base_id),
    INDEX idx_document_chunks_user_id (user_id),
    UNIQUE INDEX uk_document_chunks_document_index (document_id, chunk_index),
    INDEX idx_document_chunks_user_knowledge_base (user_id, knowledge_base_id),
    CONSTRAINT fk_document_chunks_document
        FOREIGN KEY (document_id, user_id, knowledge_base_id)
        REFERENCES documents (id, user_id, knowledge_base_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chunk_embeddings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chunk_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    dimension INT NOT NULL,
    embedding LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_chunk_embeddings_chunk_id (chunk_id),
    CONSTRAINT fk_chunk_embeddings_chunk
        FOREIGN KEY (chunk_id) REFERENCES document_chunks (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
