#!/usr/bin/env bash

set -Eeuo pipefail

readonly incoming_jar="${1:?Usage: deploy-remote.sh <incoming-jar>}"
readonly app_dir="/opt/flout"
readonly target_jar="$app_dir/flout-server.jar"
readonly backup_jar="$app_dir/flout-server.jar.previous"
readonly staged_jar="$app_dir/flout-server.jar.next"
readonly service_name="flout"
readonly health_url="http://127.0.0.1:8080/actuator/health"
readonly health_timeout_seconds=90
readonly lock_file="/var/lock/flout-deploy.lock"

# Remove only files owned by the deployment holding the process lock.
cleanup() {
  rm -f "$incoming_jar" "$staged_jar" "${BASH_SOURCE[0]}"
}

# Wait until the newly started application reports a healthy state.
wait_for_health() {
  local deadline remaining curl_timeout sleep_seconds response
  deadline=$((SECONDS + health_timeout_seconds))

  while ((SECONDS < deadline)); do
    remaining=$((deadline - SECONDS))
    curl_timeout=$((remaining < 3 ? remaining : 3))
    if response="$(curl --fail --silent --show-error --max-time "$curl_timeout" "$health_url" 2>/dev/null)" \
      && [[ "$response" == *'"status":"UP"'* ]]; then
      return 0
    fi

    remaining=$((deadline - SECONDS))
    if ((remaining > 0)); then
      sleep_seconds=$((remaining < 2 ? remaining : 2))
      sleep "$sleep_seconds"
    fi
  done
  return 1
}

if [[ $EUID -ne 0 ]]; then
  echo "This deployment script must run as root." >&2
  exit 1
fi

exec 9>"$lock_file"
if ! flock -n 9; then
  echo "Another deployment is already running." >&2
  exit 1
fi

trap cleanup EXIT

if [[ ! -s "$incoming_jar" ]]; then
  echo "Incoming JAR is missing or empty: $incoming_jar" >&2
  exit 1
fi

install -d -m 755 "$app_dir"

if [[ -f "$target_jar" ]]; then
  install -m 644 "$target_jar" "$backup_jar"
fi

install -m 644 "$incoming_jar" "$staged_jar"
mv -f "$staged_jar" "$target_jar"

if systemctl restart "$service_name" && wait_for_health; then
  echo "Deployment succeeded and the health check is UP."
  exit 0
fi

echo "Deployment health check failed. Rolling back." >&2
journalctl -u "$service_name" -n 100 --no-pager >&2 || true

if [[ ! -f "$backup_jar" ]]; then
  echo "No previous JAR is available for rollback." >&2
  exit 1
fi

install -m 644 "$backup_jar" "$staged_jar"
mv -f "$staged_jar" "$target_jar"

if systemctl restart "$service_name" && wait_for_health; then
  echo "Rollback succeeded and the previous release is healthy." >&2
else
  echo "Rollback failed. Manual recovery is required." >&2
fi

exit 1
