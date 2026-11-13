-- V1 — baseline.
-- Phase 1 has no domain tables yet; this migration exists only to establish the
-- Flyway schema history so later phases can build on a known starting point.
-- The pgvector extension itself is enabled by the database image's init script
-- (see infra/postgres/init/01-extension.sql).
DO $$
BEGIN
    NULL;
END
$$;
