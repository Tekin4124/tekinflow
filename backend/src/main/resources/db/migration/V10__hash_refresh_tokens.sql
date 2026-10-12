-- Refresh tokens are now stored as SHA-256 hex digests instead of raw UUIDs.
-- Existing rows hold raw UUID values that cannot be reversed into hashes, so we
-- clear them; clients simply re-authenticate to obtain a fresh refresh token.
TRUNCATE TABLE refresh_tokens;
ALTER TABLE refresh_tokens ALTER COLUMN token TYPE varchar(64) USING token::text;
ALTER TABLE refresh_tokens ALTER COLUMN parent_token TYPE varchar(64) USING parent_token::text;
