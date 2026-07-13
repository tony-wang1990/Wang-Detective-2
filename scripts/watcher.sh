#!/bin/sh
set -e

APP_DIR="${APP_DIR:-/app/king-detective}"
RUNTIME_DIR="$APP_DIR/runtime"
TRIGGER_FILE="$RUNTIME_DIR/update_version_trigger.flag"
ACTION_FILE="$RUNTIME_DIR/watcher_action.env"
PROCESSING_ACTION_FILE="$RUNTIME_DIR/watcher_action.env.processing"
SCHEDULE_FILE="$RUNTIME_DIR/backup_schedule.env"
LAST_BACKUP_FILE="$RUNTIME_DIR/backup_schedule.last"
HEARTBEAT_FILE="$RUNTIME_DIR/watcher_heartbeat"
SERVICE_NAME="${KING_DETECTIVE_SERVICE:-king-detective}"
IMAGE="${KING_DETECTIVE_IMAGE:-ghcr.io/tony-wang1990/wang-detective-2:main}"
REPOSITORY="${KING_DETECTIVE_GITHUB_REPOSITORY:-tony-wang1990/Wang-Detective-2}"
BRANCH="${KING_DETECTIVE_GITHUB_BRANCH:-main}"
HEALTH_URL="${KING_DETECTIVE_HEALTH_URL:-http://king-detective:9527/actuator/health}"

apk add --no-cache docker-cli-compose curl sqlite >/dev/null
mkdir -p "$RUNTIME_DIR"

echo "King-Detective Watcher"
echo "app dir: $APP_DIR"
echo "image: $IMAGE"
echo "repository: $REPOSITORY"
echo "update trigger: $TRIGGER_FILE"
echo "action file: $ACTION_FILE"
echo "health url: $HEALTH_URL"

latest_version() {
  latest_sha="$(curl -fsS "https://api.github.com/repos/$REPOSITORY/commits/$BRANCH" 2>/dev/null \
    | grep -m1 '"sha":' \
    | sed -E 's/.*"sha"[[:space:]]*:[[:space:]]*"([0-9a-fA-F]{7}).*/\1/' || true)"
  if [ -n "$latest_sha" ]; then
    printf '%s-%s' "$BRANCH" "$latest_sha"
  else
    printf '%s' "$BRANCH"
  fi
}

update_db_version() {
  db_file="$APP_DIR/data/king-detective.db"
  version_value="$(latest_version)"

  if [ ! -f "$db_file" ]; then
    echo "database not found, skip version write: $db_file"
    return
  fi

  escaped_version="$(printf '%s' "$version_value" | sed "s/'/''/g")"
  record_exists="$(sqlite3 "$db_file" "SELECT COUNT(*) FROM oci_kv WHERE code = 'Y106' AND type = 'Y003';" 2>/dev/null || echo "0")"

  if [ "$record_exists" -gt 0 ] 2>/dev/null; then
    sqlite3 "$db_file" "UPDATE oci_kv SET value = '$escaped_version' WHERE code = 'Y106' AND type = 'Y003';"
  else
    kv_id="$(date +%s)"
    sqlite3 "$db_file" "INSERT INTO oci_kv (id, code, type, value) VALUES ('$kv_id', 'Y106', 'Y003', '$escaped_version');" 2>/dev/null || true
  fi
  echo "version recorded: $version_value"
}

wait_for_health() {
  echo "waiting for service health..."
  for i in $(seq 1 90); do
    container_state="$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$SERVICE_NAME" 2>/dev/null || true)"
    if echo "$container_state" | grep -q "running healthy"; then
      echo "service health check passed"
      return 0
    fi
    if curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "service health check passed"
      return 0
    fi
    echo "  - starting... $((i * 5))s/450s $container_state"
    sleep 5
  done

  echo "service did not become healthy in time; check docker logs $SERVICE_NAME"
  return 1
}

run_update() {
  cd "$APP_DIR"
  : > "$TRIGGER_FILE"

  echo "update requested: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "[1/4] pulling image $IMAGE"
  docker compose pull "$SERVICE_NAME"

  echo "[2/4] recreating service"
  docker compose up -d --force-recreate "$SERVICE_NAME"

  echo "[3/4] waiting for service"
  wait_for_health || true

  echo "[4/4] writing version"
  update_db_version || true
  echo "update action finished"
}

read_action_value() {
  file="$1"
  key="$2"
  grep -m1 "^$key=" "$file" 2>/dev/null | cut -d= -f2- || true
}

valid_backup_name() {
  name="$1"
  [ -n "$name" ] || return 1
  case "$name" in
    */*|*\\*|*..*|*.tmp) return 1 ;;
    *.tar.gz) return 0 ;;
    *) return 1 ;;
  esac
}

install_schedule() {
  cron_schedule="$1"
  [ -n "$cron_schedule" ] || return 1
  printf 'CRON_SCHEDULE=%s\n' "$cron_schedule" > "$SCHEDULE_FILE"
  echo "backup schedule installed: $cron_schedule"
}

remove_schedule() {
  rm -f "$SCHEDULE_FILE" "$LAST_BACKUP_FILE"
  echo "backup schedule removed"
}

run_restore_action() {
  backup_name="$1"
  valid_backup_name "$backup_name" || {
    echo "invalid backup name: $backup_name"
    return 1
  }
  backup_file="$APP_DIR/backups/$backup_name"
  [ -f "$backup_file" ] || {
    echo "backup file not found: $backup_file"
    return 1
  }
  echo "restore requested: $backup_file"
  RESTORE_FROM_WATCHER=1 RESTORE_CONFIRM=YES BACKUP_FILE="$backup_file" bash "$APP_DIR/scripts/restore.sh" "$backup_file"
}

process_action_file() {
  [ -f "$ACTION_FILE" ] || return 0
  mv "$ACTION_FILE" "$PROCESSING_ACTION_FILE" 2>/dev/null || return 0

  action="$(read_action_value "$PROCESSING_ACTION_FILE" ACTION)"
  echo "watcher action: $action"

  case "$action" in
    restore)
      run_restore_action "$(read_action_value "$PROCESSING_ACTION_FILE" BACKUP_NAME)" || true
      ;;
    schedule_install)
      install_schedule "$(read_action_value "$PROCESSING_ACTION_FILE" CRON_SCHEDULE)" || true
      ;;
    schedule_remove)
      remove_schedule || true
      ;;
    *)
      echo "unknown watcher action: $action"
      ;;
  esac

  rm -f "$PROCESSING_ACTION_FILE"
}

strip_zero() {
  value="$1"
  value="${value#0}"
  [ -n "$value" ] || value="0"
  printf '%s' "$value"
}

matches_field() {
  field="$1"
  value="$2"
  for part in $(printf '%s' "$field" | tr ',' ' '); do
    case "$part" in
      "*")
        return 0
        ;;
      "*/"*)
        step="${part#*/}"
        [ "$step" -gt 0 ] 2>/dev/null || continue
        [ $((value % step)) -eq 0 ] && return 0
        ;;
      *-*)
        min="$(strip_zero "${part%-*}")"
        max="$(strip_zero "${part#*-}")"
        [ "$value" -ge "$min" ] 2>/dev/null && [ "$value" -le "$max" ] 2>/dev/null && return 0
        ;;
      *)
        wanted="$(strip_zero "$part")"
        [ "$wanted" = "$value" ] && return 0
        ;;
    esac
  done
  return 1
}

schedule_due() {
  [ -f "$SCHEDULE_FILE" ] || return 1
  cron_schedule="$(read_action_value "$SCHEDULE_FILE" CRON_SCHEDULE)"
  set -- $cron_schedule
  [ "$#" -eq 5 ] || return 1

  minute="$(strip_zero "$(date +%M)")"
  hour="$(strip_zero "$(date +%H)")"
  day="$(strip_zero "$(date +%d)")"
  month="$(strip_zero "$(date +%m)")"
  weekday="$(strip_zero "$(date +%w)")"

  matches_field "$1" "$minute" \
    && matches_field "$2" "$hour" \
    && matches_field "$3" "$day" \
    && matches_field "$4" "$month" \
    && matches_field "$5" "$weekday"
}

run_scheduled_backup_if_due() {
  schedule_due || return 0
  minute_key="$(date +%Y%m%d%H%M)"
  if [ -f "$LAST_BACKUP_FILE" ] && [ "$(cat "$LAST_BACKUP_FILE" 2>/dev/null)" = "$minute_key" ]; then
    return 0
  fi
  printf '%s' "$minute_key" > "$LAST_BACKUP_FILE"

  cd "$APP_DIR"
  echo "scheduled backup started: $(date '+%Y-%m-%d %H:%M:%S')"
  if docker compose ps "$SERVICE_NAME" 2>/dev/null | grep -q "$SERVICE_NAME"; then
    docker compose exec -T "$SERVICE_NAME" sh -lc 'cd /app/king-detective && bash scripts/backup.sh' \
      && echo "scheduled backup finished" \
      || echo "scheduled backup failed"
  else
    echo "service container not found, skip scheduled backup"
  fi
}

while true; do
  date '+%Y-%m-%d %H:%M:%S' > "$HEARTBEAT_FILE" 2>/dev/null || true

  process_action_file || true

  if [ -f "$TRIGGER_FILE" ]; then
    content="$(cat "$TRIGGER_FILE" 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
    if [ -n "$content" ]; then
      run_update
    fi
  fi

  run_scheduled_backup_if_due || true
  sleep 2
done
