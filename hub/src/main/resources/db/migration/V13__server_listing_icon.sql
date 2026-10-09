-- The modpack's image: shown on the dashboard's server page and used as the per-server
-- thumbnail in the Discord bot's /serverinfo. A square icon reads best at thumbnail size.
ALTER TABLE servers
    ADD COLUMN icon_url VARCHAR(300) NOT NULL DEFAULT '';
