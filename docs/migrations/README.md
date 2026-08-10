# OceanBase migrations

The SQL files in this directory are additive design artifacts for the controlled migration pipeline. They are not executed by the application and must not be run against production during the read-only compatibility phase.

Migration order:

1. Apply the schema in a non-production OceanBase environment.
   Apply `V1_8__governance_standard_center.sql`, `V1_9__governance_mapping_rules.sql`, `V1_10__governance_scan_runs.sql`, and then `V1_11__document_scope_and_relation.sql` after the V1.7 governance closed-loop schema.
2. Run a read-only inventory and reconciliation report against `sys_drawing`, `sys_file`, `sys_drawing_collect`, `sys_drawing_comment`, `sys_drawing_comment_like`, and `sys_file_operation_log`.
   Use `V1_5__legacy_reconciliation_queries.sql` and retain the result set as the migration batch baseline.
3. Backfill extension rows in idempotent batches, retaining every legacy ID and original value.
4. Compare source and extension counts and sample hashes before enabling dual-read.
5. Enable writes only after the object-storage, authorization, and rollback checks have passed.

V1.10 stores append-only scan run history and does not change asset or issue source values. The application uses the in-memory run store by default; the JDBC adapter is selected only when `ASSET_GOVERNANCE_SCHEMA_ENABLED=true` under the `local` or `oceanbase` profile. Automatic scheduling is disabled by default and requires `ASSET_GOVERNANCE_SCAN_ENABLED=true` plus an optional `ASSET_GOVERNANCE_SCAN_FIXED_DELAY_MS`.

V1.11 adds a document scope mode, `document_scope`, `asset_document_relation`, and `asset_document_relation_audit`. It preserves legacy document rows as `UNCLASSIFIED`, never changes legacy drawing IDs or source values, and must be validated with the same non-production migration and reconciliation controls before JDBC writing is enabled.
