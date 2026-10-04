-- Discord bot runtime config (channels, roles, branding, application rules) and its
-- display-only /serverinfo server list -- previously stored in the bot's local
-- config.json/servers.json, now owned by the hub so the bot keeps no local state.
CREATE TABLE discord_config (
    id                      INT           NOT NULL PRIMARY KEY,
    applications_channel_id VARCHAR(32),
    welcome_channel_id      VARCHAR(32),
    server_info_channel_id  VARCHAR(32),
    staff_log_channel_id    VARCHAR(32),
    member_role_id          VARCHAR(32),
    staff_role_ids          VARCHAR(1000) NOT NULL DEFAULT '',
    min_age                 INT           NOT NULL DEFAULT 18,
    apply_cooldown_days     INT           NOT NULL DEFAULT 7,
    brand_name              VARCHAR(100)  NOT NULL DEFAULT 'The Community',
    brand_color             VARCHAR(7)    NOT NULL DEFAULT '#5865f2',
    brand_icon_url          VARCHAR(300)
);

INSERT INTO discord_config (id) VALUES (1);

CREATE TABLE discord_servers (
    id             VARCHAR(32)  NOT NULL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    public_address VARCHAR(100) NOT NULL DEFAULT '',
    modpack        VARCHAR(100) NOT NULL DEFAULT '',
    version        VARCHAR(32)  NOT NULL DEFAULT '',
    install_url    VARCHAR(300) NOT NULL DEFAULT '',
    created_at     DATETIME     NOT NULL
);
