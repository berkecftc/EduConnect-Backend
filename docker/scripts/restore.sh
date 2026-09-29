#!/bin/bash
set -eo pipefail
export MSYS_NO_PATHCONV=1

usage() {
    echo "usage: $0 <backup-dir> --yes [--only postgres|volumes|rabbitmq]" >&2
    exit 1
}

source_dir="$1"
shift || usage
confirmed=false
only=""
while [ $# -gt 0 ]; do
    case "$1" in
        --yes) confirmed=true ;;
        --only) only="$2"; shift ;;
        *) usage ;;
    esac
    shift
done
[ -d "$source_dir" ] || usage
source_dir="$(cd "$source_dir" && pwd)"

postgres="${POSTGRES_CONTAINER:-educonnect-postgres}"
rabbitmq="${RABBITMQ_CONTAINER:-educonnect-rabbitmq}"
minio="${MINIO_CONTAINER:-educonnect-minio}"
databases="auth_db user_db club_db event_db course_db assignment_db post_db gamification_db"
app_services="api-gateway auth-services user-service club-service event-service course-service assignment-service post-service gamification-service notification-service llm-service"

(cd "$source_dir" && sha256sum --quiet -c SHA256SUMS)
echo "checksums ok: $source_dir"

project="$(docker inspect "$postgres" --format '{{index .Config.Labels "com.docker.compose.project"}}')"
helper_image="$(docker inspect "$postgres" --format '{{.Config.Image}}')"
admin="$(docker exec "$postgres" printenv POSTGRES_USER)"

running=""
for svc in $app_services; do
    if [ -n "$(docker ps -q --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=$svc")" ]; then
        running="$running $svc"
    fi
done
if [ -n "$running" ]; then
    echo "stop the application services first:$running" >&2
    echo "  docker compose --profile app stop$running" >&2
    exit 1
fi

if [ "$confirmed" != true ]; then
    echo "this overwrites live data; re-run with --yes" >&2
    exit 1
fi

if [ -z "$only" ] || [ "$only" = postgres ]; then
    for db in $databases; do
        [ -f "$source_dir/$db.dump" ] || continue
        owner="${db%_db}_svc"
        docker exec "$postgres" psql -v ON_ERROR_STOP=1 -q -U "$admin" -d postgres -c "DROP DATABASE IF EXISTS \"$db\" WITH (FORCE)" -c "CREATE DATABASE \"$db\" OWNER \"$owner\"" -c "REVOKE ALL ON DATABASE \"$db\" FROM PUBLIC"
        docker exec -i "$postgres" pg_restore -U "$admin" -d "$db" --exit-on-error --single-transaction < "$source_dir/$db.dump"
        echo "postgres: $db restored"
    done
fi

if [ -z "$only" ] || [ "$only" = volumes ]; then
    for archive in "$source_dir"/*.tar.gz; do
        [ -f "$archive" ] || continue
        volume="$(basename "$archive" .tar.gz)"
        name="$(docker volume ls -q --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.volume=$volume")"
        if [ -z "$name" ]; then
            echo "volume $volume not found; create it with 'docker compose up --no-start' first" >&2
            exit 1
        fi
        minio_was_running=false
        if [ "$volume" = minio-data ] && [ -n "$(docker ps -q --filter "name=^${minio}\$")" ]; then
            docker stop "$minio" > /dev/null
            minio_was_running=true
        fi
        docker run --rm -i -v "$name:/data" "$helper_image" sh -c 'find /data -mindepth 1 -delete && tar xzf - -C /data' < "$archive"
        if [ "$minio_was_running" = true ]; then
            docker start "$minio" > /dev/null
        fi
        echo "volume: $volume restored"
    done
fi

if [ -z "$only" ] || [ "$only" = rabbitmq ]; then
    if [ -f "$source_dir/rabbitmq-definitions.json" ]; then
        docker exec -i "$rabbitmq" sh -c 'cat > /tmp/definitions.json' < "$source_dir/rabbitmq-definitions.json"
        docker exec "$rabbitmq" rabbitmqctl -q import_definitions /tmp/definitions.json
        docker exec "$rabbitmq" rm -f /tmp/definitions.json
        echo "rabbitmq: definitions imported"
    fi
fi

echo "restore finished; start the application with: docker compose --profile app up -d"
