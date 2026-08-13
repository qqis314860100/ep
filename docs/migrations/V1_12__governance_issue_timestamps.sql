-- V1.12 governance issue detection/update timestamps. Additive only.
-- Execute through the controlled migration pipeline; do not run from application startup.
-- 历史存量问题的发现时间不可追溯，按迁移执行时间兜底填充。

ALTER TABLE governance_issue
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '问题首次发现时间（历史存量按迁移时间兜底）',
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '问题最近变更时间（重开/领取/解决/事实刷新）';
