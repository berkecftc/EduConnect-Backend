#!/bin/bash
set -eo pipefail
export MSYS_NO_PATHCONV=1

action="${1:-apply}"
if [ "$action" != "apply" ] && [ "$action" != "remove" ]; then
    echo "usage: $0 [apply|remove]" >&2
    exit 1
fi

container="${POSTGRES_CONTAINER:-educonnect-postgres}"
minio="${MINIO_CONTAINER:-educonnect-minio}"
club_bucket="${CLUB_BUCKET:-club-bucket}"
seed_dir="$(cd "$(dirname "$0")/../dev-seed/$action" && pwd)"
logo_dir="$(cd "$(dirname "$0")/../dev-seed/assets/club-logos" && pwd)"
admin="$(docker exec "$container" printenv POSTGRES_USER)"

for file in "$seed_dir"/*.sql; do
    db="$(basename "$file" .sql)"
    docker exec -i "$container" psql -v ON_ERROR_STOP=1 -q -U "$admin" -d "$db" < "$file"
    echo "$action: $db"
done

if ! docker ps --format '{{.Names}}' | grep -qx "$minio"; then
    echo "$action: club logos skipped ($minio is not running)"
    exit 0
fi
docker exec "$minio" sh -c 'mc alias set seed http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null'
for logo in "$logo_dir"/*.png; do
    name="$(basename "$logo")"
    if [ "$action" = "apply" ]; then
        docker exec -i "$minio" sh -c "cat > /tmp/$name" < "$logo"
        docker exec "$minio" sh -c "mc mb --ignore-existing seed/$club_bucket >/dev/null && mc cp --quiet --attr 'Content-Type=image/png' /tmp/$name seed/$club_bucket/logos/$name >/dev/null && rm /tmp/$name"
    else
        docker exec "$minio" mc rm --quiet "seed/$club_bucket/logos/$name" >/dev/null 2>&1 || true
    fi
done
echo "$action: club logos"
