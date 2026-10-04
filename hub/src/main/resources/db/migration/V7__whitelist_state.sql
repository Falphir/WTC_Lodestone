-- Tracks whether the network whitelist has ever had a player on it, separately from whether
-- it's currently empty. Lets the bridge tell "nobody's set this up yet" apart from "deliberately
-- emptied" -- servers should leave their local whitelist alone for the former, but wipe it for
-- the latter.
CREATE TABLE whitelist_state (
    id             INT     NOT NULL PRIMARY KEY,
    ever_populated BOOLEAN NOT NULL DEFAULT FALSE
);

-- Backfill for existing hubs: if the whitelist currently has anyone on it, or the hub already has
-- servers registered (so it's clearly in active use, whitelist emptied or not), treat it as
-- already configured. Only a genuinely fresh hub -- no servers, no whitelist entries -- starts false.
INSERT INTO whitelist_state (id, ever_populated)
SELECT 1, (SELECT COUNT(*) FROM whitelisted_players) > 0 OR (SELECT COUNT(*) FROM servers) > 0;
