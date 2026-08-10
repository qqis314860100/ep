-- V1.11 document scope and asset-document relation extensions. Additive only.
-- Execute through the controlled migration pipeline; do not run from application startup.

ALTER TABLE knowledge_document
    ADD COLUMN scope_mode VARCHAR(32) NOT NULL DEFAULT 'UNCLASSIFIED'
        COMMENT 'GLOBAL, SPECIFIED, or legacy UNCLASSIFIED';

CREATE TABLE IF NOT EXISTS document_scope (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    platform_family VARCHAR(100) NULL,
    platform_variant VARCHAR(100) NULL,
    product_line VARCHAR(100) NULL COMMENT 'Legacy-compatible blueprints field',
    base_name VARCHAR(200) NULL,
    production_line VARCHAR(200) NULL,
    process_section VARCHAR(200) NULL,
    source_value_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_scope (document_id, platform_family, platform_variant, product_line, base_name, production_line, process_section),
    KEY idx_document_scope_document (document_id),
    KEY idx_document_scope_platform (platform_family, platform_variant, product_line),
    KEY idx_document_scope_location (base_name, production_line, process_section)
) COMMENT='Document application scope; no legacy source fields are overwritten';

CREATE TABLE IF NOT EXISTS asset_document_relation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL COMMENT 'Stable sys_drawing ID',
    document_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL COMMENT 'COMPANION, APPLICABLE, REFERENCE',
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_document_relation (drawing_id, document_id, relation_type),
    KEY idx_asset_document_relation_asset (drawing_id, deleted_at),
    KEY idx_asset_document_relation_document (document_id, deleted_at)
) COMMENT='Structured relation between an asset and a knowledge document';

CREATE TABLE IF NOT EXISTS asset_document_relation_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    relation_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL COMMENT 'CREATE, CHANGE_TYPE, REMOVE, RESTORE',
    before_value_json JSON NULL,
    after_value_json JSON NULL,
    operated_by VARCHAR(100) NOT NULL,
    operated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_asset_document_relation_audit_relation (relation_id, id)
) COMMENT='Append-only audit record for asset document relation changes';
