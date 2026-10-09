-- Lets the dashboard change a server's id: without ON UPDATE CASCADE here, renaming the id would
-- be blocked by this FK the moment the server had any heartbeat history.
ALTER TABLE server_heartbeats
    DROP FOREIGN KEY fk_server_heartbeats_server;
ALTER TABLE server_heartbeats
    ADD CONSTRAINT fk_server_heartbeats_server FOREIGN KEY (server_id) REFERENCES servers (id) ON UPDATE CASCADE;
