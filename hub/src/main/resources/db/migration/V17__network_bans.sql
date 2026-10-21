-- The network-wide ban list. Every server applies it, but only ever adds and removes the bans it
-- got from here (tagged with the source "WTC Lodestone"), so a server's own /ban bans are left alone.
CREATE TABLE network_bans (
    uuid       VARCHAR(36)  NOT NULL PRIMARY KEY,
    name       VARCHAR(16)  NOT NULL,
    reason     VARCHAR(255) NULL,
    banned_by  VARCHAR(64)  NOT NULL,
    banned_at  DATETIME(6)  NOT NULL,
    -- NULL is permanent; past this the servers lift the ban themselves
    expires_at DATETIME(6)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Singleton row (id always 1): what a network-banned player is told. NULL is the built-in default
-- (BanService.DEFAULT_MESSAGE); bans a server made itself keep Minecraft's own wording.
CREATE TABLE ban_settings (
    id      INT          NOT NULL PRIMARY KEY,
    message VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO ban_settings (id, message) VALUES (1, NULL);

-- Per server, the same as for the whitelist: whether it applies the list (its syncBans config) and
-- which version of the list it last applied, so the dashboard can show who's behind.
ALTER TABLE servers
    ADD COLUMN sync_bans      BOOLEAN     NULL,
    ADD COLUMN ban_version    VARCHAR(64) NULL,
    ADD COLUMN bans_synced_at DATETIME(6) NULL;
