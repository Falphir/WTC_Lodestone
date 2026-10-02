-- Network-wide whitelist: every server running the mod mirrors this list.
CREATE TABLE whitelisted_players (
    uuid      CHAR(36)    NOT NULL,
    name      VARCHAR(16) NOT NULL,
    added_by  VARCHAR(64) NOT NULL,
    added_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
