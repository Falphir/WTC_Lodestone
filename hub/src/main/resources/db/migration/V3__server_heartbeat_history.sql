ALTER TABLE servers
    DROP COLUMN player_count,
    DROP COLUMN max_players,
    DROP COLUMN tps,
    DROP COLUMN memory_used_mb,
    DROP COLUMN memory_max_mb;

CREATE TABLE server_heartbeats (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    server_id       VARCHAR(64)  NOT NULL,
    recorded_at     DATETIME(6)  NOT NULL,
    player_count    INT          NOT NULL,
    max_players     INT          NOT NULL,
    tps             DOUBLE       NOT NULL,
    memory_used_mb  INT          NOT NULL,
    memory_max_mb   INT          NOT NULL,
    PRIMARY KEY (id),
    KEY idx_server_heartbeats_server_id_recorded_at (server_id, recorded_at),
    CONSTRAINT fk_server_heartbeats_server FOREIGN KEY (server_id) REFERENCES servers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
