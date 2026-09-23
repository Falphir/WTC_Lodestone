CREATE TABLE servers (
                         id          VARCHAR(64)  NOT NULL,
                         name        VARCHAR(100) NOT NULL,
                         token_hash  VARCHAR(64)  NOT NULL,
                         enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
                         created_at  DATETIME(6)  NOT NULL,
                         last_seen   DATETIME(6)  NULL,
                         PRIMARY KEY (id),
                         UNIQUE KEY uk_servers_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;