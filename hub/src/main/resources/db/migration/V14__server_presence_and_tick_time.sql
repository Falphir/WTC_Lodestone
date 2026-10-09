-- enforce-whitelist out of each server's server.properties, reported by the mod on check-in.
-- With it off, whitelist sync still adds and removes rows but never kicks anyone, so a player
-- removed on the dashboard stays connected -- worth showing rather than leaving silent.
ALTER TABLE servers
    ADD COLUMN enforce_whitelist BOOLEAN NULL;

-- Average tick time in milliseconds. tps saturates at 20 for as long as a tick fits inside its
-- 50ms budget, so on its own it can't show a server getting slower until it is already lagging.
ALTER TABLE server_heartbeats
    ADD COLUMN mspt_avg DOUBLE NOT NULL DEFAULT 0;
