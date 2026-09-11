-- Fresh local databases need a disabled catalog template before the first data standard can be enabled.
-- This is governance configuration, not business/demo data. Enabling a standard copies this row into
-- an immutable enabled catalog snapshot.
INSERT INTO governance_rule_catalog (
  data_standard_id,
  data_standard_version,
  field_rule_version,
  dictionary_versions_json,
  quality_policy_id,
  quality_policy_version,
  enabled,
  version
)
SELECT
  'E2E-FIELD-COMPLETENESS',
  0,
  1,
  '{}',
  'FIELD-QUALITY',
  1,
  0,
  0
WHERE NOT EXISTS (
  SELECT 1
  FROM governance_rule_catalog
  WHERE data_standard_id = 'E2E-FIELD-COMPLETENESS'
);
