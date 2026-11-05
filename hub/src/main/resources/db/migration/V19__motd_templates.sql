-- The network's MOTDs: each a text with {modpack}, {version} and {name} filled in per server. One can be
-- the default, preselected when a server is created. A server picks one, or none and keeps its own.
CREATE TABLE motd_templates (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(60)  NOT NULL,
    text       VARCHAR(300) NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_motd_templates_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- No ON DELETE: a MOTD in use can't be deleted, the hub says which servers use it instead.
ALTER TABLE servers
    ADD COLUMN motd_template_id BIGINT NULL,
    ADD CONSTRAINT fk_servers_motd_template FOREIGN KEY (motd_template_id) REFERENCES motd_templates (id);
