-- Read-only reconciliation queries for the V1.5 migration rehearsal.
-- Run in a reporting connection only. Do not add UPDATE/DELETE/INSERT statements here.

-- 1. Source row counts: capture as the migration baseline.
SELECT 'sys_drawing' AS source_table, COUNT(*) AS row_count FROM sys_drawing
UNION ALL SELECT 'sys_file', COUNT(*) FROM sys_file
UNION ALL SELECT 'sys_drawing_collect', COUNT(*) FROM sys_drawing_collect
UNION ALL SELECT 'sys_drawing_comment', COUNT(*) FROM sys_drawing_comment
UNION ALL SELECT 'sys_drawing_comment_like', COUNT(*) FROM sys_drawing_comment_like
UNION ALL SELECT 'sys_file_operation_log', COUNT(*) FROM sys_file_operation_log;

-- 2. Source quality profile: empty identifiers and values that need governance.
SELECT
    COUNT(*) AS total_drawings,
    SUM(CASE WHEN id IS NULL THEN 1 ELSE 0 END) AS null_ids,
    SUM(CASE WHEN drawing_title IS NULL OR TRIM(drawing_title) = '' THEN 1 ELSE 0 END) AS empty_titles,
    SUM(CASE WHEN drawing_url IS NULL OR TRIM(drawing_url) = '' THEN 1 ELSE 0 END) AS missing_source_files,
    SUM(CASE WHEN drawing_platform IS NULL OR TRIM(drawing_platform) = '' THEN 1 ELSE 0 END) AS missing_legacy_platform,
    SUM(CASE WHEN drawing_line IS NULL OR TRIM(drawing_line) = '' THEN 1 ELSE 0 END) AS missing_legacy_line
FROM sys_drawing;

-- 3. Orphan behavior records: isolate for review; never delete them automatically.
SELECT 'collect_without_drawing' AS issue_type, COUNT(*) AS row_count
FROM sys_drawing_collect c LEFT JOIN sys_drawing d ON d.id = c.drawing_id
WHERE d.id IS NULL
UNION ALL
SELECT 'comment_without_drawing', COUNT(*)
FROM sys_drawing_comment c LEFT JOIN sys_drawing d ON d.id = c.drawing_id
WHERE d.id IS NULL
UNION ALL
SELECT 'like_without_comment', COUNT(*)
FROM sys_drawing_comment_like l LEFT JOIN sys_drawing_comment c ON c.id = l.comment_id
WHERE c.id IS NULL
UNION ALL
SELECT 'operation_log_without_drawing', COUNT(*)
FROM sys_file_operation_log l LEFT JOIN sys_drawing d ON d.id = l.drawing_id
WHERE l.drawing_id IS NOT NULL AND d.id IS NULL;

-- 4. Duplicate source references: flag possible duplicates without merging.
SELECT drawing_url AS source_reference, COUNT(*) AS drawing_count
FROM sys_drawing
WHERE drawing_url IS NOT NULL AND TRIM(drawing_url) <> ''
GROUP BY drawing_url
HAVING COUNT(*) > 1
ORDER BY drawing_count DESC;

-- 5. Extension comparison after a rehearsal backfill.
SELECT 'asset_package_ext_without_source' AS issue_type, COUNT(*) AS row_count
FROM asset_package_ext e LEFT JOIN sys_drawing d ON d.id = e.drawing_id
WHERE d.id IS NULL
UNION ALL
SELECT 'scope_ext_without_source', COUNT(*)
FROM asset_scope_ext e LEFT JOIN sys_drawing d ON d.id = e.drawing_id
WHERE d.id IS NULL
UNION ALL
SELECT 'file_ext_without_source', COUNT(*)
FROM asset_file_ext e LEFT JOIN sys_drawing d ON d.id = e.drawing_id
WHERE d.id IS NULL;

