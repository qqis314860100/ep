-- V1.6 additive document-center schema for OceanBase MySQL mode.
-- Review and execute through the controlled migration pipeline only.
-- This file never changes legacy primary keys or source values.

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_number VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    maintainer_id VARCHAR(64) NOT NULL DEFAULT '',
    maintainer_name VARCHAR(100) NOT NULL,
    maintainer_department VARCHAR(100) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_document_number (document_number),
    KEY idx_knowledge_document_search (status, category_code, updated_at),
    KEY idx_knowledge_document_maintainer (maintainer_id, status)
) COMMENT='Stable knowledge-document metadata';

CREATE TABLE IF NOT EXISTS document_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_number VARCHAR(40) NOT NULL,
    change_summary VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_by VARCHAR(100) NOT NULL DEFAULT '',
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_version_number (document_id, version_number),
    KEY idx_document_version_status (document_id, status, published_at)
) COMMENT='Immutable file version of a knowledge document';

CREATE TABLE IF NOT EXISTS document_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    original_name VARCHAR(1000) NOT NULL,
    format VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    previewable TINYINT(1) NOT NULL DEFAULT 0,
    storage_key VARCHAR(500) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_document_file_document (document_id, version_id),
    KEY idx_document_file_hash (content_sha256)
) COMMENT='Controlled file references belonging to document versions';

INSERT IGNORE INTO dictionary_item
    (id, category_code, item_code, item_name, parent_id, sort_order, usage_count,
     forward_name, reverse_name, directional, allow_duplicate)
VALUES
    (261, 'DOCUMENT_CATEGORY', 'TECHNICAL_SPECIFICATION', '技术规范', NULL, 10, 0, NULL, NULL, 0, 0),
    (262, 'DOCUMENT_CATEGORY', 'MANUAL', '说明书', NULL, 20, 0, NULL, NULL, 0, 0),
    (263, 'DOCUMENT_CATEGORY', 'WORK_INSTRUCTION', '作业指导书', NULL, 30, 0, NULL, NULL, 0, 0),
    (264, 'DOCUMENT_CATEGORY', 'COMMISSIONING', '调试资料', NULL, 40, 0, NULL, NULL, 0, 0),
    (265, 'DOCUMENT_CATEGORY', 'ACCEPTANCE', '验收资料', NULL, 50, 0, NULL, NULL, 0, 0),
    (266, 'DOCUMENT_CATEGORY', 'STANDARD_TEMPLATE', '标准模板', NULL, 60, 0, NULL, NULL, 0, 0);
