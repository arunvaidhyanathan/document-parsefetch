-- liquibase formatted sql

-- changeset arun:2
-- comment: Create performance_metrics table for tracking parsing and search performance

-- Performance Metrics Sequence
CREATE SEQUENCE IF NOT EXISTS document_management.performance_metrics_seq
    START WITH 1
    INCREMENT BY 1;

-- Performance Metrics Table
CREATE TABLE IF NOT EXISTS document_management.performance_metrics (
    id BIGINT PRIMARY KEY DEFAULT nextval('document_management.performance_metrics_seq'),
    operation_type VARCHAR(50) NOT NULL,
    document_id BIGINT,
    file_size_bytes BIGINT,
    total_time_ms BIGINT NOT NULL,
    db_time_ms BIGINT,
    parsing_time_ms BIGINT,
    tika_parse_time_ms BIGINT,
    chunks_created INTEGER,
    metadata_keys_extracted INTEGER,
    search_query TEXT,
    results_count INTEGER,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for Performance Metrics
CREATE INDEX IF NOT EXISTS idx_perf_operation_type
    ON document_management.performance_metrics(operation_type);

CREATE INDEX IF NOT EXISTS idx_perf_status
    ON document_management.performance_metrics(status);

CREATE INDEX IF NOT EXISTS idx_perf_created_at
    ON document_management.performance_metrics(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_perf_document_id
    ON document_management.performance_metrics(document_id)
    WHERE document_id IS NOT NULL;

-- rollback DROP TABLE IF EXISTS document_management.performance_metrics CASCADE;
-- rollback DROP SEQUENCE IF EXISTS document_management.performance_metrics_seq CASCADE;
