-- Who changed what, and when: one row per admin change, from the dashboard or the bot. Rows are only
-- ever inserted, and deleted by AuditRetentionTask once older than lodestone.audit-retention-days.
CREATE TABLE audit_log (
    id      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    at      DATETIME(6)   NOT NULL,
    -- the hub account that made the call; "system" for anything outside a request
    actor   VARCHAR(64)   NOT NULL,
    -- for the bot's calls, the Discord staff member it acted for: "name (id)"
    via     VARCHAR(120)  NULL,
    action  VARCHAR(40)   NOT NULL,
    target  VARCHAR(120)  NOT NULL,
    detail  VARCHAR(500)  NULL,
    KEY idx_audit_log_at (at)
);
