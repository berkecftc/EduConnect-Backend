#!/bin/bash
set -eo pipefail

services="auth user club event course assignment post gamification"

for svc in $services; do
    password_var="$(echo "$svc" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
    password="${!password_var}"
    if [ -z "$password" ]; then
        echo "$password_var is not set" >&2
        exit 1
    fi
    role="${svc}_svc"
    db="${svc}_db"

    psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname postgres \
        -v role="$role" -v db="$db" -v password="$password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'role', :'password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'role')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db') \gexec
SQL

    psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname "$db" \
        -c "REVOKE ALL ON SCHEMA public FROM PUBLIC"
    echo "database $db ready (owner $role)"
done

for db in postgres "${POSTGRES_DB:-postgres}"; do
    psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname postgres \
        -c "REVOKE CONNECT ON DATABASE \"$db\" FROM PUBLIC"
done
