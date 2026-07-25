-- V1.5 additive extension schema for OceanBase MySQL mode.
-- Review and execute through the controlled migration pipeline only.
-- This file must not be run against production during the read-only phase.

CREATE TABLE IF NOT EXISTS asset_package_ext (
    drawing_id BIGINT NOT NULL COMMENT 'Stable ID from sys_drawing',
    asset_number VARCHAR(200) NULL,
    asset_type VARCHAR(40) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_CURATION',
    module_tags JSON NULL,
    standard_equipment_module TINYINT(1) NOT NULL DEFAULT 0,
    linked_module_asset_ids JSON NULL,
    equipment_interconnect_code VARCHAR(200) NULL,
    owner_user_id VARCHAR(100) NULL,
    owner_department VARCHAR(200) NULL,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (drawing_id),
    KEY idx_asset_package_status (status),
    KEY idx_asset_package_interconnect (equipment_interconnect_code)
) COMMENT='Normalized extension fields; never overwrites sys_drawing';

CREATE TABLE IF NOT EXISTS asset_scope_ext (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL,
    platform_family VARCHAR(100) NOT NULL,
    platform_variant VARCHAR(100) NULL,
    product_line VARCHAR(100) NULL COMMENT 'Legacy-compatible field storing blueprint code or name',
    base_name VARCHAR(200) NULL,
    production_line VARCHAR(200) NULL,
    process_section VARCHAR(200) NULL,
    source_value_json JSON NULL COMMENT 'Original scope text when mapped from legacy data',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_scope (drawing_id, platform_family, platform_variant, product_line, base_name, production_line, process_section),
    KEY idx_scope_platform (platform_family, platform_variant),
    KEY idx_scope_location (base_name, production_line, process_section),
    KEY idx_scope_drawing (drawing_id)
) COMMENT='Normalized product and production scope dimensions';

CREATE TABLE IF NOT EXISTS asset_module_link_ext (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_drawing_id BIGINT NOT NULL,
    target_drawing_id BIGINT NOT NULL,
    link_type VARCHAR(40) NOT NULL DEFAULT 'MODULE_REFERENCE',
    description VARCHAR(1000) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_link (source_drawing_id, target_drawing_id, link_type),
    KEY idx_module_link_target (target_drawing_id)
) COMMENT='Hyperlinks between module assets; does not copy files';

CREATE TABLE IF NOT EXISTS asset_equipment_interconnect_ext (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL,
    equipment_code VARCHAR(200) NOT NULL,
    equipment_name VARCHAR(300) NULL,
    base_name VARCHAR(200) NULL,
    production_line VARCHAR(200) NULL,
    process_section VARCHAR(200) NULL,
    interconnect_data_ref VARCHAR(1000) NULL,
    source_value_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_interconnect (drawing_id, equipment_code),
    KEY idx_equipment_line (base_name, production_line, process_section),
    KEY idx_equipment_code (equipment_code)
) COMMENT='Equipment-to-line data interconnection mapping';

CREATE TABLE IF NOT EXISTS asset_file_ext (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL,
    legacy_file_id BIGINT NULL COMMENT 'Original sys_file ID when available',
    original_name VARCHAR(1000) NOT NULL,
    display_name VARCHAR(1000) NULL,
    format VARCHAR(40) NULL,
    role VARCHAR(60) NULL,
    storage_key VARCHAR(500) NULL,
    content_sha256 CHAR(64) NULL,
    size_bytes BIGINT NULL,
    previewable TINYINT(1) NOT NULL DEFAULT 0,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    file_status VARCHAR(40) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_asset_file_drawing (drawing_id),
    KEY idx_asset_file_hash (content_sha256),
    KEY idx_asset_file_legacy (legacy_file_id)
) COMMENT='Normalized asset file manifest; original files remain in object storage';

CREATE TABLE IF NOT EXISTS asset_audit_ext (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NULL,
    actor_user_id VARCHAR(100) NOT NULL,
    action VARCHAR(80) NOT NULL,
    payload_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_asset_audit_drawing (drawing_id, created_at),
    KEY idx_asset_audit_actor (actor_user_id, created_at)
) COMMENT='New audit events; legacy sys_file_operation_log remains unchanged';

CREATE TABLE IF NOT EXISTS dictionary_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_code VARCHAR(80) NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    parent_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    usage_count BIGINT NOT NULL DEFAULT 0,
    description VARCHAR(500) NULL,
    forward_name VARCHAR(200) NULL,
    reverse_name VARCHAR(200) NULL,
    directional TINYINT(1) NOT NULL DEFAULT 0,
    allow_duplicate TINYINT(1) NOT NULL DEFAULT 0,
    merge_target_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dictionary_category_code (category_code, item_code),
    KEY idx_dictionary_category_status (category_code, status, sort_order),
    KEY idx_dictionary_parent (parent_id, status),
    KEY idx_dictionary_merge_target (merge_target_id)
) COMMENT='Controlled product, production and asset dictionaries';
