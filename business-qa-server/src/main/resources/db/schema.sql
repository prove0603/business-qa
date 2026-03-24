-- Business QA Database Schema
-- Database: business_qa
-- Requires: PostgreSQL 16+ with pgvector extension

CREATE EXTENSION IF NOT EXISTS vector;

-- Module configuration
CREATE TABLE IF NOT EXISTS t_module (
    id BIGSERIAL PRIMARY KEY,
    module_name VARCHAR(100) NOT NULL,
    module_code VARCHAR(100) NOT NULL UNIQUE,
    git_remote_url VARCHAR(500),
    git_branch VARCHAR(100) DEFAULT 'master',
    module_type VARCHAR(20) NOT NULL,
    description TEXT,
    last_sync_commit VARCHAR(64),
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_module IS 'Business system module configuration';
COMMENT ON COLUMN t_module.module_type IS 'COMMON = auto-included in queries, TASK = user-selectable';

-- Business documents
CREATE TABLE IF NOT EXISTS t_document (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    file_type VARCHAR(20) DEFAULT 'MARKDOWN',
    content_hash VARCHAR(64),
    original_filename VARCHAR(300),
    content_type VARCHAR(100),
    file_key VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    chunk_count INT DEFAULT 0,
    vectorized BOOLEAN DEFAULT FALSE,
    version INT DEFAULT 1,
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_module_id ON t_document(module_id);

-- Change detection records
CREATE TABLE IF NOT EXISTS t_change_detection (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL,
    from_commit VARCHAR(64),
    to_commit VARCHAR(64),
    changed_files TEXT,
    changed_file_count INT DEFAULT 0,
    suggestion_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_detection_module_id ON t_change_detection(module_id);

-- AI document modification suggestions
CREATE TABLE IF NOT EXISTS t_change_suggestion (
    id BIGSERIAL PRIMARY KEY,
    detection_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    affected_section VARCHAR(500),
    original_text TEXT,
    suggested_text TEXT,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_suggestion_detection_id ON t_change_suggestion(detection_id);
CREATE INDEX idx_change_suggestion_status ON t_change_suggestion(status);

-- Chat sessions
CREATE TABLE IF NOT EXISTS t_chat_session (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    module_filter TEXT,
    message_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat messages
CREATE TABLE IF NOT EXISTS t_chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    source_refs TEXT,
    tokens_used INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_message_session_id ON t_chat_message(session_id);

-- Vector table (qa_vector) is auto-created by Spring AI PgVectorStore
