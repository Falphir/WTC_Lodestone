ALTER TABLE servers
    ADD COLUMN player_count  INT    NULL,
    ADD COLUMN max_players   INT    NULL,
    ADD COLUMN tps           DOUBLE NULL,
    ADD COLUMN memory_used_mb INT   NULL,
    ADD COLUMN memory_max_mb  INT   NULL;
