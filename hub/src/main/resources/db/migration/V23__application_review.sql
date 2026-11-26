-- What the dashboard's Applications page shows, deleted with the row by ApplicationRetentionTask.
-- answers is the /apply form as a JSON object; rows from before this migration have none.
ALTER TABLE discord_applications
    ADD COLUMN discord_name VARCHAR(64) NULL,
    ADD COLUMN answers VARCHAR(4000) NULL,
    ADD COLUMN deny_reason VARCHAR(500) NULL,
    -- a decision made off Discord (the dashboard) that the bot has yet to act on: role, DM, post
    ADD COLUMN bot_pending BOOLEAN NOT NULL DEFAULT FALSE;
