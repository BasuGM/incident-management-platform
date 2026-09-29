-- Flyway baseline: migration infrastructure only (no domain tables).

CREATE SCHEMA IF NOT EXISTS app;

COMMENT ON SCHEMA app IS 'Application schema for Developer Incident Management Platform';
