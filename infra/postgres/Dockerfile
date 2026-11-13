# Postgres 16 with the pgvector extension, plus a baked-in init script that
# enables the extension on first start of a fresh data volume.
FROM pgvector/pgvector:pg16

COPY init/01-extension.sql /docker-entrypoint-initdb.d/01-extension.sql
