-- Folds discord_servers into servers. A listing is just the public face of a server that's
-- already registered here, so two tables meant entering every server twice -- once on the
-- dashboard for its token, once through the bot's /server to be listed -- under two unrelated
-- ids that nothing kept in step. `published` is what /serverinfo now filters on.
ALTER TABLE servers
    ADD COLUMN public_address  VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN modpack         VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN modpack_url     VARCHAR(300) NOT NULL DEFAULT '',
    ADD COLUMN modpack_version VARCHAR(32)  NOT NULL DEFAULT '',
    ADD COLUMN launcher        VARCHAR(32)  NOT NULL DEFAULT '',
    ADD COLUMN published       BOOLEAN      NOT NULL DEFAULT FALSE;

-- Carry across the listings whose ids already line up, and leave them visible, since they
-- were being listed before this ran. A listing with no matching server has nowhere to go:
-- it was never a real server here, so it has to be re-entered against one.
--
-- discord_servers (V8) never pinned a collation and so took this server's default (MariaDB 11
-- changed it to utf8mb4_uca1400_ai_ci), which doesn't match servers' utf8mb4_unicode_ci from
-- V1 -- without COLLATE the join fails with "Illegal mix of collations", same as in V10.
UPDATE servers s
JOIN discord_servers d ON d.id COLLATE utf8mb4_unicode_ci = s.id
SET s.public_address = d.public_address,
    s.modpack = d.modpack,
    s.modpack_url = d.install_url,
    s.modpack_version = d.version,
    s.published = TRUE;

DROP TABLE discord_servers;
