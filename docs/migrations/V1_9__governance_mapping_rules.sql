-- V1.9 versioned mapping rules. Additive only; source values remain unchanged.
CREATE TABLE IF NOT EXISTS governance_mapping_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  standard_id BIGINT NOT NULL,
  standard_code VARCHAR(100) NOT NULL,
  standard_version BIGINT NOT NULL,
  rule_version BIGINT NOT NULL,
  source_dimension VARCHAR(80) NOT NULL,
  source_value VARCHAR(500) NOT NULL,
  status VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  payload_json JSON NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_mapping_version (standard_id, standard_version, source_dimension, source_value, rule_version),
  KEY idx_governance_mapping_status (status, source_dimension)
);
