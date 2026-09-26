#!/bin/bash
set -eo pipefail
export MSYS_NO_PATHCONV=1

action="${1:-apply}"
if [ "$action" != "apply" ] && [ "$action" != "remove" ]; then
    echo "usage: $0 [apply|remove]" >&2
    exit 1
fi

container="${POSTGRES_CONTAINER:-educonnect-postgres}"
seed_dir="$(cd "$(dirname "$0")/../dev-seed/$action" && pwd)"
admin="$(docker exec "$container" printenv POSTGRES_USER)"

for file in "$seed_dir"/*.sql; do
    db="$(basename "$file" .sql)"
    docker exec -i "$container" psql -v ON_ERROR_STOP=1 -q -U "$admin" -d "$db" < "$file"
    echo "$action: $db"
done
