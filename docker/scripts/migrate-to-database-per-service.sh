#!/bin/bash
set -eo pipefail
export MSYS_NO_PATHCONV=1

container="${POSTGRES_CONTAINER:-educonnect-postgres}"
source_db="${SOURCE_DB:-postgres}"
services="auth user club event course assignment post gamification"

admin="$(docker exec "$container" printenv POSTGRES_USER)"

pg() {
    docker exec "$container" psql -v ON_ERROR_STOP=1 -q -At -U "$admin" "$@"
}

schema_exists() {
    pg -d "$1" -c "SELECT count(*) FROM information_schema.schemata WHERE schema_name = '$2'"
}

echo "== creating databases and roles"
docker exec "$container" bash /docker-entrypoint-initdb.d/01-create-databases.sh

failed=0
for svc in $services; do
    schema="${svc}_db"
    role="${svc}_svc"
    echo "== $schema"

    if [ "$(schema_exists "$source_db" "$schema")" = "0" ]; then
        echo "   source schema $source_db.$schema not found, skipped"
        continue
    fi
    if [ "$(schema_exists "$schema" "$schema")" != "0" ]; then
        echo "   $schema already has schema $schema, skipped"
        continue
    fi

    docker exec "$container" bash -c \
        "set -o pipefail; pg_dump -U '$admin' -d '$source_db' -n '$schema' --no-owner --no-privileges | psql -v ON_ERROR_STOP=1 -q -U '$role' -d '$schema' > /dev/null"

    tables="$(pg -d "$source_db" -c "SELECT table_name FROM information_schema.tables WHERE table_schema = '$schema' AND table_type = 'BASE TABLE' ORDER BY 1")"
    for table in $tables; do
        before="$(pg -d "$source_db" -c "SELECT count(*) FROM \"$schema\".\"$table\"")"
        after="$(pg -d "$schema" -c "SELECT count(*) FROM \"$schema\".\"$table\"")"
        if [ "$before" = "$after" ]; then
            echo "   $table: $after rows"
        else
            echo "   $table: MISMATCH source=$before target=$after"
            failed=1
        fi
    done
done

if [ "$failed" != "0" ]; then
    echo "row counts differ, do not switch the services to the new databases" >&2
    exit 1
fi
echo "done; the old schemas in database '$source_db' were left untouched"
