-- Local MySQL bootstrap for the V1.5 development environment.
-- Idempotent: creates missing structures and never drops or truncates data.

CREATE DATABASE IF NOT EXISTS tianshu
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE tianshu;

CREATE TABLE IF NOT EXISTS sys_drawing (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_title VARCHAR(500) NULL,
    drawing_content TEXT NULL,
    drawing_url VARCHAR(500) NULL,
    drawing_img VARCHAR(2000) NULL,
    drawing_carousel VARCHAR(500) NULL,
    drawing_column VARCHAR(1000) NULL,
    drawing_purpose VARCHAR(200) NULL,
    drawing_format VARCHAR(100) NULL,
    drawing_label VARCHAR(1000) NULL,
    drawing_platform VARCHAR(200) NULL,
    drawing_line VARCHAR(200) NULL,
    drawing_beat VARCHAR(200) NULL,
    drawing_appearance VARCHAR(200) NULL,
    drawing_match VARCHAR(500) NULL,
    drawing_exchange VARCHAR(500) NULL,
    drawing_technology TEXT NULL,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    created_by_name VARCHAR(100) NULL,
    last_update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_by BIGINT NULL,
    last_updated_by_name VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_drawing_title (drawing_title),
    KEY idx_drawing_platform (drawing_platform),
    KEY idx_drawing_line (drawing_line),
    KEY idx_drawing_format (drawing_format)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Legacy-compatible drawing metadata';

CREATE TABLE IF NOT EXISTS sys_drawing_collect (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    last_update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_by BIGINT NULL,
    last_updated_by_name VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drawing_user (drawing_id, created_by),
    KEY idx_collect_created_by (created_by),
    KEY idx_collect_drawing_id (drawing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Drawing favorites';

CREATE TABLE IF NOT EXISTS sys_drawing_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_img VARCHAR(2000) NULL,
    comment_content TEXT NULL,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    last_update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_by BIGINT NULL,
    last_updated_by_name VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_comment_drawing_id (drawing_id, creation_date),
    KEY idx_comment_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Drawing comments with soft deletion';

CREATE TABLE IF NOT EXISTS sys_drawing_comment_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100) NULL,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_by BIGINT NULL,
    last_updated_by_name VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_user (comment_id, created_by),
    KEY idx_like_comment_id (comment_id),
    KEY idx_like_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-user comment likes';

CREATE TABLE IF NOT EXISTS sys_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_name VARCHAR(200) NULL,
    file_url VARCHAR(500) NULL,
    file_type VARCHAR(100) NULL,
    file_size BIGINT NULL,
    file_path VARCHAR(500) NULL,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    last_updated_by BIGINT NULL,
    last_update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='File metadata';

CREATE TABLE IF NOT EXISTS sys_file_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NULL,
    file_name VARCHAR(255) NULL,
    file_url VARCHAR(500) NULL,
    file_type VARCHAR(100) NULL,
    file_size BIGINT NULL,
    file_path VARCHAR(500) NULL,
    operation_type VARCHAR(100) NOT NULL,
    operated_by BIGINT NOT NULL DEFAULT -1,
    operation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT -1,
    created_by_name VARCHAR(100) NULL,
    last_updated_by BIGINT NOT NULL DEFAULT -1,
    last_update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_by_name VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_operation_drawing (drawing_id, operation_date),
    KEY idx_operation_actor (operated_by, operation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='File and drawing operation audit log';

CREATE TABLE IF NOT EXISTS temp_person (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(256) NULL,
    phone VARCHAR(256) NULL,
    status INT NOT NULL DEFAULT 1,
    memo VARCHAR(500) NULL,
    object_version_number BIGINT NOT NULL DEFAULT 1,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT -1,
    last_updated_by BIGINT NOT NULL DEFAULT -1,
    last_update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_person_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Local employee directory placeholder';

CREATE TABLE IF NOT EXISTS asset_package_ext (
    drawing_id BIGINT NOT NULL,
    asset_number VARCHAR(200) NULL,
    asset_type VARCHAR(40) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_CURATION',
    module_tags JSON NULL,
    standard_equipment_module TINYINT(1) NOT NULL DEFAULT 0,
    linked_module_asset_ids JSON NULL,
    equipment_interconnect_code VARCHAR(200) NULL,
    owner_user_id VARCHAR(100) NULL,
    owner_department VARCHAR(200) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (drawing_id),
    KEY idx_asset_package_status (status),
    KEY idx_asset_package_interconnect (equipment_interconnect_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Normalized extension fields';

CREATE TABLE IF NOT EXISTS asset_scope_ext (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL,
    platform_family VARCHAR(100) NOT NULL,
    platform_variant VARCHAR(100) NULL,
    product_line VARCHAR(100) NULL,
    base_name VARCHAR(200) NULL,
    production_line VARCHAR(200) NULL,
    process_section VARCHAR(200) NULL,
    source_value_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_scope (drawing_id, platform_family(64), platform_variant(64), product_line(64), base_name(64), production_line(64), process_section(64)),
    KEY idx_scope_platform (platform_family, platform_variant),
    KEY idx_scope_location (base_name, production_line, process_section),
    KEY idx_scope_drawing (drawing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Normalized product and production scopes';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Links between module assets';

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
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_interconnect (drawing_id, equipment_code),
    KEY idx_equipment_line (base_name, production_line, process_section),
    KEY idx_equipment_code (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Equipment-to-line data links';

CREATE TABLE IF NOT EXISTS asset_file_ext (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drawing_id BIGINT NOT NULL,
    legacy_file_id BIGINT NULL,
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
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_asset_file_drawing (drawing_id),
    KEY idx_asset_file_hash (content_sha256),
    KEY idx_asset_file_legacy (legacy_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Normalized asset file manifest';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V1.5 audit events';

CREATE TABLE IF NOT EXISTS governance_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_number VARCHAR(64) NOT NULL,
    name VARCHAR(300) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    scope_description VARCHAR(1000) NULL,
    owner_user_id VARCHAR(100) NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    assignee_id VARCHAR(100) NULL,
    start_date DATE NULL,
    due_date DATE NULL,
    target_quantity BIGINT NOT NULL DEFAULT 0,
    completed_quantity BIGINT NOT NULL DEFAULT 0,
    quantity_unit VARCHAR(40) NOT NULL DEFAULT '资产',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_task_number (task_number),
    KEY idx_governance_task_status (status, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Data governance task header';

CREATE TABLE IF NOT EXISTS governance_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    sequence_number INT NOT NULL,
    name VARCHAR(300) NOT NULL,
    responsible_user_id VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    due_date DATE NOT NULL,
    target_quantity BIGINT NOT NULL,
    completed_quantity BIGINT NOT NULL DEFAULT 0,
    quantity_unit VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    completed_at DATE NULL,
    actual_start DATE NULL,
    actual_end DATE NULL,
    dependency_ids JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_plan_sequence (task_id, sequence_number),
    KEY idx_governance_plan_task (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Governance task plan and progress';

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
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dictionary_category_code (category_code, item_code),
    KEY idx_dictionary_category_status (category_code, status, sort_order),
    KEY idx_dictionary_parent (parent_id, status),
    KEY idx_dictionary_merge_target (merge_target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Controlled business dictionary items';
