#!/bin/bash
set -eo pipefail
export MSYS_NO_PATHCONV=1
umask 077

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
backup_root="${BACKUP_DIR:-$root_dir/backups}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"
postgres="${POSTGRES_CONTAINER:-educonnect-postgres}"
rabbitmq="${RABBITMQ_CONTAINER:-educonnect-rabbitmq}"
databases="auth_db user_db club_db event_db course_db assignment_db post_db gamification_db"
volumes="minio-data llm-data"

project="$(docker inspect "$postgres" --format '{{index .Config.Labels "com.docker.compose.project"}}')"
helper_image="$(docker inspect "$postgres" --format '{{.Config.Image}}')"
admin="$(docker exec "$postgres" printenv POSTGRES_USER)"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$backup_root/$stamp"
partial="$target.partial"
mkdir -p "$partial"
trap 'rm -rf "$partial"' EXIT

docker exec "$postgres" pg_dumpall -U "$admin" --globals-only > "$partial/globals.sql"
echo "postgres: globals"

for db in $databases; do
    docker exec "$postgres" pg_dump -U "$admin" -d "$db" -Fc > "$partial/$db.dump"
    echo "postgres: $db"
done

for volume in $volumes; do
    name="$(docker volume ls -q --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.volume=$volume")"
    if [ -z "$name" ]; then
        echo "volume $volume not found, skipped" >&2
        continue
    fi
    docker run --rm -v "$name:/data:ro" "$helper_image" tar czf - -C /data . > "$partial/$volume.tar.gz"
    echo "volume: $volume"
done

if docker exec "$rabbitmq" rabbitmqctl -q export_definitions /tmp/definitions.json > /dev/null; then
    docker exec "$rabbitmq" cat /tmp/definitions.json > "$partial/rabbitmq-definitions.json"
    docker exec "$rabbitmq" rm -f /tmp/definitions.json
    echo "rabbitmq: definitions"
fi

{
    echo "created=$stamp"
    echo "git_revision=$(cd "$root_dir" && git rev-parse HEAD 2>/dev/null || echo unknown)"
    echo "postgres_image=$helper_image"
} > "$partial/manifest.txt"

(cd "$partial" && sha256sum -- *.dump *.sql *.tar.gz *.json manifest.txt 2>/dev/null > SHA256SUMS)

mv "$partial" "$target"
trap - EXIT
echo "backup written to $target ($(du -sh "$target" | cut -f1))"

if [ "$retention_days" -gt 0 ]; then
    find "$backup_root" -mindepth 1 -maxdepth 1 -type d -name '20*Z' -mtime +"$retention_days" -print -exec rm -rf {} +
fi
