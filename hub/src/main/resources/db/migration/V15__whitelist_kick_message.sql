-- The network-wide kick message for players who aren't whitelisted. NULL leaves each server on its
-- own mod config's whitelistMessage, which is what every server already had before this column.
ALTER TABLE whitelist_state ADD COLUMN kick_message VARCHAR(500) NULL;
