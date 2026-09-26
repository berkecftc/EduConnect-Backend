#!/bin/sh
set -eu

esc() {
  printf '%s' "$1" | sed -e "s/'/''/g" -e 's/[\\|&]/\\&/g'
}

sed \
  -e "s|__SMTP_SMARTHOST__|$(esc "${ALERT_SMTP_SMARTHOST:-mailpit:1025}")|" \
  -e "s|__EMAIL_FROM__|$(esc "${ALERT_EMAIL_FROM:-alerts@educonnect.local}")|" \
  -e "s|__EMAIL_TO__|$(esc "${ALERT_EMAIL_TO:-ops@educonnect.local}")|" \
  -e "s|__SMTP_USERNAME__|$(esc "${ALERT_SMTP_USERNAME:-}")|" \
  -e "s|__SMTP_PASSWORD__|$(esc "${ALERT_SMTP_PASSWORD:-}")|" \
  -e "s|__SMTP_REQUIRE_TLS__|${ALERT_SMTP_REQUIRE_TLS:-false}|" \
  /etc/alertmanager/alertmanager.yml.tmpl > /tmp/alertmanager.yml

exec /bin/alertmanager --config.file=/tmp/alertmanager.yml --storage.path=/alertmanager "$@"
