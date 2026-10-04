-- Discord<->Minecraft account links and in-flight applications -- previously the Discord
-- bot's local SQLite (links.db), now owned by the hub so the bot keeps no local state at all.
-- Timestamps are epoch millis (not DATETIME) to match what the bot already works with.
CREATE TABLE discord_links (
    discord_id  VARCHAR(32) NOT NULL PRIMARY KEY,
    mc_uuid     VARCHAR(36) NOT NULL UNIQUE,
    mc_username VARCHAR(16) NOT NULL,
    linked_at   BIGINT      NOT NULL
);

CREATE TABLE discord_applications (
    discord_id  VARCHAR(32) NOT NULL PRIMARY KEY,
    status      VARCHAR(16) NOT NULL,
    message_id  VARCHAR(32),
    mc_uuid     VARCHAR(36),
    mc_username VARCHAR(16),
    created_at  BIGINT      NOT NULL,
    decided_at  BIGINT
);
