-- V1.7 governance closed-loop schema for MySQL-compatible OceanBase.
-- Additive and idempotent; never changes legacy primary keys or source values.

ALTER TABLE asset_package_ext ADD COLUMN IF NOT EXISTS standard_description TEXT NULL;
ALTER TABLE asset_package_ext ADD COLUMN IF NOT EXISTS standard_specialties JSON NULL;
ALTER TABLE asset_package_ext ADD COLUMN IF NOT EXISTS standard_value_updated_at DATETIME(6) NULL;
ALTER TABLE asset_scope_ext ADD COLUMN IF NOT EXISTS source_type VARCHAR(40) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE asset_scope_ext ADD COLUMN IF NOT EXISTS governance_result_version_id BIGINT NULL;
ALTER TABLE asset_scope_ext ADD COLUMN IF NOT EXISTS active TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE governance_task ADD COLUMN IF NOT EXISTS workflow_version VARCHAR(40) NOT NULL DEFAULT 'LEGACY_PROGRESS';
ALTER TABLE governance_task ADD COLUMN IF NOT EXISTS current_round INT NOT NULL DEFAULT 1;
ALTER TABLE governance_task ADD COLUMN IF NOT EXISTS scope_snapshot_id BIGINT NULL;
ALTER TABLE governance_task ADD COLUMN IF NOT EXISTS quality_policy_snapshot_id BIGINT NULL;
ALTER TABLE governance_task ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE governance_task ADD COLUMN IF NOT EXISTS issue_type VARCHAR(80) NULL;
ALTER TABLE governance_plan ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS asset_responsibility_ext (
  id BIGINT NOT NULL AUTO_INCREMENT, drawing_id BIGINT NOT NULL, responsible_user_id VARCHAR(100) NOT NULL,
  responsibility_scope VARCHAR(200) NOT NULL, governance_result_version_id BIGINT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1, version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY(id), UNIQUE KEY uk_asset_responsibility_active(drawing_id, active), KEY idx_responsibility_user(responsible_user_id, active)
);
CREATE TABLE IF NOT EXISTS governance_issue (
  id BIGINT NOT NULL AUTO_INCREMENT, asset_id BIGINT NOT NULL, target_field VARCHAR(40) NOT NULL,
  issue_type VARCHAR(80) NOT NULL, target_path VARCHAR(300) NOT NULL, rule_code VARCHAR(100) NOT NULL,
  rule_version BIGINT NOT NULL, original_fact_json JSON NOT NULL, asset_version BIGINT NOT NULL,
  scope_fingerprint VARCHAR(128) NOT NULL, severity VARCHAR(40) NOT NULL, blocking TINYINT(1) NOT NULL,
  status VARCHAR(40) NOT NULL, task_id BIGINT NULL, fingerprint VARCHAR(255) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY(id), UNIQUE KEY uk_governance_issue_fingerprint(fingerprint), KEY idx_issue_pool(status,target_field,asset_id), KEY idx_issue_task(task_id,status)
);
CREATE TABLE IF NOT EXISTS governance_plan_item (
  plan_id BIGINT NOT NULL, issue_id BIGINT NOT NULL, PRIMARY KEY(plan_id,issue_id), UNIQUE KEY uk_plan_issue(issue_id)
);
CREATE TABLE IF NOT EXISTS governance_rule_catalog (
  id BIGINT NOT NULL AUTO_INCREMENT, data_standard_id VARCHAR(100) NOT NULL, data_standard_version BIGINT NOT NULL,
  field_rule_version BIGINT NOT NULL, dictionary_versions_json JSON NOT NULL, quality_policy_id VARCHAR(100) NOT NULL,
  quality_policy_version BIGINT NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY(id), KEY idx_rule_catalog_enabled(enabled,id)
);
CREATE TABLE IF NOT EXISTS governance_rule_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, payload_json JSON NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(id), KEY idx_rule_snapshot_task(task_id)
);
CREATE TABLE IF NOT EXISTS governance_scope_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, created_by VARCHAR(100) NOT NULL,
  frozen_at DATETIME(6) NOT NULL, item_count INT NOT NULL, payload_json JSON NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_scope_snapshot_task(task_id)
);
CREATE TABLE IF NOT EXISTS governance_scope_item (
  id BIGINT NOT NULL AUTO_INCREMENT, snapshot_id BIGINT NOT NULL, task_id BIGINT NOT NULL, issue_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL, target_field VARCHAR(40) NOT NULL, payload_json JSON NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_scope_item_issue(snapshot_id,issue_id), KEY idx_scope_item_task(task_id,asset_id)
);
CREATE TABLE IF NOT EXISTS governance_item (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, issue_id BIGINT NOT NULL, asset_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL, governance_round INT NOT NULL, current_result_version_id BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0, payload_json JSON NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_governance_item_issue(task_id,issue_id), KEY idx_governance_item_task(task_id,status,id)
);
CREATE TABLE IF NOT EXISTS governance_result_version (
  id BIGINT NOT NULL AUTO_INCREMENT, item_id BIGINT NOT NULL, governance_round INT NOT NULL, result_version INT NOT NULL,
  status VARCHAR(40) NOT NULL, version BIGINT NOT NULL DEFAULT 0, payload_json JSON NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_result_item_version(item_id,result_version), KEY idx_result_item_status(item_id,status)
);
CREATE TABLE IF NOT EXISTS governance_confirmation_round (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, governance_round INT NOT NULL, status VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0, payload_json JSON NOT NULL, PRIMARY KEY(id),
  UNIQUE KEY uk_confirmation_task_round(task_id,governance_round), KEY idx_confirmation_current(task_id,status)
);
CREATE TABLE IF NOT EXISTS governance_confirmation_decision (
  id BIGINT NOT NULL AUTO_INCREMENT, round_id BIGINT NOT NULL, item_id BIGINT NOT NULL, result_version_id BIGINT NOT NULL,
  decision VARCHAR(40) NOT NULL, version BIGINT NOT NULL DEFAULT 0, payload_json JSON NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_confirmation_decision(round_id,item_id)
);
CREATE TABLE IF NOT EXISTS governance_acceptance_round (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, governance_round INT NOT NULL, status VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0, payload_json JSON NOT NULL, PRIMARY KEY(id),
  UNIQUE KEY uk_acceptance_task_round(task_id,governance_round), KEY idx_acceptance_current(task_id,status)
);
CREATE TABLE IF NOT EXISTS governance_quality_policy (
  id BIGINT NOT NULL AUTO_INCREMENT, policy_code VARCHAR(100) NOT NULL, policy_version BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0, policy_json JSON NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_quality_policy(policy_code,policy_version)
);
CREATE TABLE IF NOT EXISTS governance_quality_policy_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, policy_json JSON NOT NULL, PRIMARY KEY(id), KEY idx_quality_snapshot_task(task_id)
);
CREATE TABLE IF NOT EXISTS governance_data_standard (
  id BIGINT NOT NULL AUTO_INCREMENT, standard_code VARCHAR(100) NOT NULL, standard_version BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0, standard_json JSON NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_data_standard(standard_code,standard_version)
);
CREATE TABLE IF NOT EXISTS governance_field_rule (
  id BIGINT NOT NULL AUTO_INCREMENT, standard_id BIGINT NOT NULL, target_field VARCHAR(40) NOT NULL,
  rule_version BIGINT NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 0, rule_json JSON NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_field_rule(standard_id,target_field,rule_version)
);
CREATE TABLE IF NOT EXISTS governance_operation_job (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, acceptance_round_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL, version BIGINT NOT NULL DEFAULT 0, payload_json JSON NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_operation_acceptance(acceptance_round_id), KEY idx_operation_task(task_id,status)
);
CREATE TABLE IF NOT EXISTS governance_operation_job_item (
  job_id BIGINT NOT NULL, item_id BIGINT NOT NULL, result_version_id BIGINT NOT NULL, status VARCHAR(40) NOT NULL,
  error_message VARCHAR(1000) NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(job_id,item_id)
);
CREATE TABLE IF NOT EXISTS governance_audit_event (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, item_id BIGINT NULL, aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id BIGINT NOT NULL, action VARCHAR(80) NOT NULL, governance_round INT NOT NULL, actor_user_id VARCHAR(100) NOT NULL,
  before_json JSON NOT NULL, after_json JSON NOT NULL, created_at DATETIME(6) NOT NULL,
  PRIMARY KEY(id), KEY idx_governance_audit_task(task_id,created_at,id), KEY idx_governance_audit_item(item_id,created_at)
);

INSERT IGNORE INTO governance_rule_catalog
  (id,data_standard_id,data_standard_version,field_rule_version,dictionary_versions_json,quality_policy_id,quality_policy_version,enabled)
VALUES (1,'FIELD-COMPLETENESS',1,1,'{"specialty":5,"scope":8}','FIELD-QUALITY',2,1);
