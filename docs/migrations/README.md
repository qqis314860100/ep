# OceanBase migrations

The SQL files in this directory are additive design artifacts for the controlled migration pipeline. They are not executed by the application and must not be run against production during the read-only compatibility phase.

Migration order:

1. Apply the schema in a non-production OceanBase environment.
2. Run a read-only inventory and reconciliation report against `sys_drawing`, `sys_file`, `sys_drawing_collect`, `sys_drawing_comment`, `sys_drawing_comment_like`, and `sys_file_operation_log`.
   Use `V1_5__legacy_reconciliation_queries.sql` and retain the result set as the migration batch baseline.
3. Backfill extension rows in idempotent batches, retaining every legacy ID and original value.
4. Compare source and extension counts and sample hashes before enabling dual-read.
5. Enable writes only after the object-storage, authorization, and rollback checks have passed.
