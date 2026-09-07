-- Standard center lifecycle and impact-review facts. Apply only in a controlled non-production migration.
ALTER TABLE governance_data_standard
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER enabled;

CREATE TABLE IF NOT EXISTS governance_standard_impact_review (
  id BIGINT NOT NULL AUTO_INCREMENT,
  standard_id BIGINT NOT NULL,
  affected_asset_count BIGINT NOT NULL DEFAULT 0,
  asset_ids_json JSON NOT NULL,
  status VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_standard_impact_review (standard_id, status, created_at)
);

INSERT IGNORE INTO governance_data_standard
  (id, standard_code, standard_version, enabled, version, standard_json)
VALUES
  (1, 'FIELD-COMPLETENESS', 1, 1, 0,
   '{"id":1,"standardCode":"FIELD-COMPLETENESS","standardVersion":1,"name":"数模资产完整性标准","status":"ENABLED","applicableAssetTypes":["THREE_DIMENSIONAL_MODEL","TWO_DIMENSIONAL_DRAWING","MIXED_ASSET"],"ownerUserId":"emp-zhang","ownerName":"张伟","effectiveAt":"2026-08-01T00:00:00Z","changeSummary":"建立治理任务字段补充与质量验收基线","affectedAssetCount":0,"rules":[{"targetField":"specialty","ruleType":"REQUIRED","description":"专业类别必须来自启用字典","blocking":true,"configurationJson":"{\"dictionary\":\"SPECIALTY\"}"},{"targetField":"scope","ruleType":"REQUIRED","description":"适用范围必须匹配完整产品和生产范围","blocking":true,"configurationJson":"{}"},{"targetField":"fileRole","ruleType":"FILE_ROLE","description":"每个资产包需要明确主文件和文件角色","blocking":true,"configurationJson":"{}"}],"version":0,"createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-01T00:00:00Z"}');
