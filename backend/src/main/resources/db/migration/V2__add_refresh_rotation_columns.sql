-- Supports refresh token rotation with a short grace window.
-- previous_refresh_token_hash: the secret that was just replaced, accepted for a few seconds
--   so two browser tabs refreshing at the same moment don't log the user out.
-- rotated_at: when the last rotation happened, to know if that grace window is still open.
ALTER TABLE sessions
    ADD COLUMN previous_refresh_token_hash VARCHAR(64) NULL AFTER refresh_token_hash,
    ADD COLUMN rotated_at DATETIME(6) NULL AFTER last_used_at;
