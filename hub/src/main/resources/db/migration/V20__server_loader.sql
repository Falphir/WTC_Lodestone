-- Which loader a server's mod runs on ("neoforge", "forge"), as the mod reports it in hello, so the
-- dashboard names it next to the loader's version. NULL until it says, and from mods older than this.
ALTER TABLE servers ADD COLUMN loader VARCHAR(32) NULL AFTER minecraft_version;
