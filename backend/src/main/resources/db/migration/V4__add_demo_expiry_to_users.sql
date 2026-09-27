-- Demo users are real users with a delete-after time. Normal users have NULL here.
ALTER TABLE users
    ADD COLUMN demo_expires_at DATETIME(6) NULL AFTER base_currency,
    ADD INDEX idx_users_demo_expires_at (demo_expires_at);
