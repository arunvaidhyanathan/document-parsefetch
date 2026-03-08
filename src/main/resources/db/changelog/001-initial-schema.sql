-- liquibase formatted sql

-- changeset arun:1
-- comment: Create document_management schema and initial tables

-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS document_management;

-- Document ID Sequence
CREATE SEQUENCE IF NOT EXISTS document_management.doc_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Main Document Records Table
CREATE TABLE IF NOT EXISTS document_management.document_records (
    id BIGINT PRIMARY KEY DEFAULT nextval('document_management.doc_id_seq'),
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    classification VARCHAR(50),
    metadata JSONB,
    parsed_content JSONB,
    error_log TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- GIN Indexes for JSONB Performance
-- Using jsonb_path_ops for optimal containment queries (@>)
CREATE INDEX IF NOT EXISTS idx_doc_metadata_gin
    ON document_management.document_records
    USING GIN (metadata jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_doc_content_gin
    ON document_management.document_records
    USING GIN (parsed_content jsonb_path_ops);

-- Full-Text Search Index for advanced keyword matching
CREATE INDEX IF NOT EXISTS idx_doc_fts_content
    ON document_management.document_records
    USING GIN (to_tsvector('english', COALESCE(parsed_content::text, '')));

-- B-Tree Indexes for Status Filtering
CREATE INDEX IF NOT EXISTS idx_doc_status
    ON document_management.document_records(status);

CREATE INDEX IF NOT EXISTS idx_doc_classification
    ON document_management.document_records(classification);

CREATE INDEX IF NOT EXISTS idx_doc_created_at
    ON document_management.document_records(created_at DESC);

-- rollback DROP SCHEMA IF EXISTS document_management CASCADE;
