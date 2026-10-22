-- Which PufferPanel server (its 8-character identifier) a hub server runs as. Optional; NULL is
-- unlinked, and a unique key allows any number of NULLs, so one panel server belongs to at most
-- one hub server.
ALTER TABLE servers
    ADD COLUMN pufferpanel_id VARCHAR(8) NULL,
    ADD UNIQUE KEY uk_servers_pufferpanel_id (pufferpanel_id);
