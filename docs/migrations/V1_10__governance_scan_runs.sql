-- V1.10 automatic governance scan run history. Additive only.
CREATE TABLE IF NOT EXISTS governance_scan_run (
  id BIGINT NOT NULL AUTO_INCREMENT,
  trigger_type VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  started_at DATETIME(6) NULL,
  finished_at DATETIME(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  payload_json JSON NOT NULL,
  PRIMARY KEY (id),
  KEY idx_governance_scan_run_status (status, id)
);
