-- Folds discord_links into whitelisted_players: a Discord link is only ever meaningful for an
-- account that's actually whitelisted, and every /link always re-applies the whitelist for the
-- account it's switching to -- so keeping them as two tables just invited the kind of staleness
-- bug this fixes (removing a player from the whitelist left its Discord link row behind).
ALTER TABLE whitelisted_players
    ADD COLUMN discord_id VARCHAR(32) NULL,
    ADD COLUMN discord_linked_at BIGINT NULL,
    ADD CONSTRAINT uq_whitelisted_players_discord_id UNIQUE (discord_id);

-- discord_links (V9) didn't pin a collation and picked up this server's default (MariaDB 11
-- changed it to utf8mb4_uca1400_ai_ci), which doesn't match whitelisted_players' utf8mb4_unicode_ci
-- (pinned explicitly since V5) -- without COLLATE here the join fails with "Illegal mix of collations".
UPDATE whitelisted_players w
JOIN discord_links l ON l.mc_uuid COLLATE utf8mb4_unicode_ci = w.uuid
SET w.discord_id = l.discord_id, w.discord_linked_at = l.linked_at;

DROP TABLE discord_links;
