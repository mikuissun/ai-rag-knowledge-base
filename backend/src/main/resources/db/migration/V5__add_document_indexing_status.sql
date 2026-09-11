ALTER TABLE documents
    ADD COLUMN indexing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER processed_at,
    ADD COLUMN indexing_error VARCHAR(1000) NULL AFTER indexing_status,
    ADD COLUMN indexed_at DATETIME NULL AFTER indexing_error;

CREATE INDEX idx_documents_indexing_status ON documents (indexing_status);
