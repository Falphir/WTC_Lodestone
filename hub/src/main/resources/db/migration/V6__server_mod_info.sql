-- What each server reports about itself on check-in, and which whitelist version it last applied.
ALTER TABLE servers
    ADD COLUMN mod_version          VARCHAR(32) NULL,
    ADD COLUMN minecraft_version    VARCHAR(32) NULL,
    ADD COLUMN loader_version       VARCHAR(32) NULL,
    ADD COLUMN sync_whitelist       BOOLEAN     NULL,
    ADD COLUMN whitelist_version    VARCHAR(64) NULL,
    ADD COLUMN whitelist_synced_at  DATETIME(6) NULL;
