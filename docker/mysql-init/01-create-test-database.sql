-- Runs once, the first time the MySQL container starts with an empty data volume.
-- Creates a separate database for automated tests so they never touch your dev data.
CREATE DATABASE IF NOT EXISTS finledger_test;
GRANT ALL PRIVILEGES ON finledger_test.* TO 'finledger'@'%';
FLUSH PRIVILEGES;
