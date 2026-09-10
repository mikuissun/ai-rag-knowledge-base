CREATE TABLE documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    content_text LONGTEXT NOT NULL,
    status INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_documents_user_id (user_id),
    INDEX idx_documents_knowledge_base_id (knowledge_base_id),
    INDEX idx_documents_user_knowledge_base (user_id, knowledge_base_id),
    CONSTRAINT fk_documents_knowledge_base FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
