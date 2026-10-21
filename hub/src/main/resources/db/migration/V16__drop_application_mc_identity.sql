-- An application no longer decides which Minecraft account gets in: players link their own with a
-- code shown when they first try to join, and approval only grants the member role. The name an
-- applicant typed stays in their public post, which message_id still points at.
ALTER TABLE discord_applications DROP COLUMN mc_uuid, DROP COLUMN mc_username;
