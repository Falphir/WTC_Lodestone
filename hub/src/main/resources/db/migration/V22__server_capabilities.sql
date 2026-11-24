-- What the server's mod build says it supports ("whitelist", "bans", ...), comma-separated, as hello
-- reports it. NULL from mods older than the field, which the hub reads as the 1.0 set.
ALTER TABLE servers ADD COLUMN capabilities VARCHAR(1000) NULL AFTER loader_version;
