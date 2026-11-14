#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

readonly backup_dir=${BACKUP_DIR:-/backups}
readonly dump_dir=$backup_dir/dump
readonly binlog_dir=$backup_dir/binlog
readonly keys_dir=$backup_dir/keys
readonly spool_dir=${SPOOL_DIR:-/spool}
readonly metrics_dir=${METRICS_DIR:-/metrics}
readonly public_key=${PUBLIC_KEY_FILE:-/run/configs/backup-public-key.asc}
readonly password_file=${DB_PASSWORD_FILE:-/run/secrets/backup_db_password}
readonly db_host=${DB_HOST:-monolith.database}
readonly db_port=${DB_PORT:-3306}
readonly db_user=${DB_USER:-backup}
read -r -a schemas <<< "${SCHEMAS:-user authorization business appointments notifications}"
readonly schemas
read -r -a key_schemas <<< "${KEY_SCHEMAS:-user_short_retention business_short_retention appointments_short_retention notifications_short_retention}"
readonly key_schemas
readonly retention_days=${RETENTION_DAYS:-30}
readonly key_retention_days=${KEY_RETENTION_DAYS:-7}
readonly dump_hour_utc=${DUMP_HOUR_UTC:-3}
readonly rotate_seconds=${BINLOG_ROTATE_SECONDS:-300}
readonly poll_seconds=${POLL_SECONDS:-15}
readonly stream_server_id=${STREAM_SERVER_ID:-4242}
readonly private_dir=/dev/shm/backup
readonly client_config=$private_dir/client.cnf
export GNUPGHOME=$private_dir/gnupg

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }

fail() {
  log "ERROR $*"
  exit 1
}

prepare() {
  umask 077
  mkdir -p "$private_dir" "$GNUPGHOME" "$dump_dir" "$binlog_dir" "$keys_dir"
  [[ -s $public_key ]] || fail "No public key at $public_key"
  [[ -s $password_file ]] || fail "No database password at $password_file"
  printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\n' "$db_host" "$db_port" "$db_user" "$(<"$password_file")" > "$client_config"
}

sql() { mariadb --defaults-extra-file="$client_config" --batch --skip-column-names -e "$1"; }

encrypt() {
  zstd -q -c | gpg --batch --no-tty --quiet --trust-model always --recipient-file "$public_key" --compress-algo none --encrypt
}

publish() {
  local target=$1
  [[ ! -e $target ]] || fail "$target already exists. A database restored onto a new volume numbers its binlogs from 1 again, see the restore runbook"
  sync "$target.partial"
  mv "$target.partial" "$target"
}

write_metrics() {
  local name=$1
  shift
  printf '%s\n' "$@" > "$metrics_dir/.$name.prom.tmp"
  chmod 0644 "$metrics_dir/.$name.prom.tmp"
  mv "$metrics_dir/.$name.prom.tmp" "$metrics_dir/$name.prom"
}

newest_file() { find "$1" -maxdepth 1 -type f -name "$2" -printf '%f\n' | sort | tail -n 1; }

successor() {
  local name=$1
  printf '%s.%06d\n' "${name%.*}" "$((10#${name##*.} + 1))"
}

first_binlog_to_stream() {
  local on_server last next
  mapfile -t on_server < <(sql 'SHOW BINARY LOGS' | cut -f1)
  ((${#on_server[@]} > 0)) || fail "Binary logging is off on $db_host"
  last=$(newest_file "$binlog_dir" '*.zst.gpg')
  if [[ -z $last ]]; then
    printf '%s\n' "${on_server[0]}"
    return
  fi
  next=$(successor "${last%.zst.gpg}")
  if printf '%s\n' "${on_server[@]}" | grep -qxF "$next"; then
    printf '%s\n' "$next"
    return
  fi
  log "ERROR $next, the binlog after the last one shipped, is no longer on $db_host. A restore can't replay past ${last%.zst.gpg} until it starts from a dump taken after this"
  printf '%s\n' "${on_server[0]}"
}

ship_closed_binlogs() {
  local files count i
  mapfile -t files < <(find "$spool_dir" -maxdepth 1 -type f -printf '%f\n' | sort)
  count=${#files[@]}
  for ((i = 0; i < count - 1; i++)); do
    encrypt < "$spool_dir/${files[i]}" > "$binlog_dir/${files[i]}.zst.gpg.partial"
    publish "$binlog_dir/${files[i]}.zst.gpg"
    rm -f "$spool_dir/${files[i]}"
    last_shipped_at=$(date +%s)
    log "Shipped ${files[i]}"
  done
}

rotate_if_written() {
  [[ $(sql 'SHOW MASTER STATUS' | cut -f1,2) != "$status_after_rotation" ]] || return 0
  sql 'FLUSH BINARY LOGS'
  status_after_rotation=$(sql 'SHOW MASTER STATUS' | cut -f1,2)
}

streaming_current_binlog() {
  local current
  current=$(sql 'SHOW MASTER STATUS' | cut -f1)
  [[ -n $current && $current == "$(newest_file "$spool_dir" '*')" ]]
}

run_binlog() {
  local start streamer status next_rotation
  start=$(first_binlog_to_stream)
  log "Streaming binlogs from $db_host, starting at $start"
  mariadb-binlog --defaults-extra-file="$client_config" --read-from-remote-server --raw --stop-never \
    --stop-never-slave-server-id="$stream_server_id" --result-file="$spool_dir/" "$start" &
  streamer=$!
  trap 'kill "$streamer" 2>/dev/null; exit 0' TERM INT
  last_shipped_at=0
  status_after_rotation=
  next_rotation=$(($(date +%s) + rotate_seconds))
  while true; do
    sleep "$poll_seconds" &
    wait $!
    if ! kill -0 "$streamer" 2>/dev/null; then
      status=0
      wait "$streamer" || status=$?
      fail "mariadb-binlog stopped with status $status"
    fi
    if (($(date +%s) >= next_rotation)); then
      rotate_if_written
      next_rotation=$(($(date +%s) + rotate_seconds))
    fi
    ship_closed_binlogs
    if streaming_current_binlog; then
      write_metrics backup_binlog \
        "bookk_backup_binlog_healthy_timestamp_seconds $(date +%s)" \
        "bookk_backup_binlog_last_shipped_timestamp_seconds $last_shipped_at"
    fi
  done
}

prune() {
  local cutoff dumps count i oldest keep_from binlog
  cutoff=dump-$(date -u -d "-$retention_days days" +%Y%m%dT%H%M%SZ)
  mapfile -t dumps < <(find "$dump_dir" -maxdepth 1 -type f -name 'dump-*.sql.zst.gpg' -printf '%f\n' | sort)
  count=${#dumps[@]}
  for ((i = 0; i < count - 1; i++)); do
    [[ ${dumps[i]} < $cutoff ]] || break
    rm -f "$dump_dir/${dumps[i]}" "$dump_dir/${dumps[i]%.sql.zst.gpg}.position"
    log "Removed ${dumps[i]}, older than $retention_days days"
  done
  oldest=$(find "$dump_dir" -maxdepth 1 -type f -name 'dump-*.sql.zst.gpg' -printf '%f\n' | sort | head -n 1)
  [[ -n $oldest ]] || return 0
  keep_from=$(cut -d' ' -f1 "$dump_dir/${oldest%.sql.zst.gpg}.position")
  while read -r binlog; do
    [[ ${binlog%.zst.gpg} < $keep_from ]] || break
    rm -f "$binlog_dir/$binlog"
  done < <(find "$binlog_dir" -maxdepth 1 -type f -name '*.zst.gpg' -printf '%f\n' | sort)
  find "$dump_dir" "$binlog_dir" -maxdepth 1 -type f -name '*.partial' -mmin +1440 -delete
}

existing_key_schemas() {
  local names
  names=$(printf "'%s'," "${key_schemas[@]}")
  sql "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN (${names%,}) ORDER BY schema_name"
}

prune_keys() {
  local cutoff dumps count i
  cutoff=keys-$(date -u -d "-$key_retention_days days" +%Y%m%dT%H%M%SZ)
  mapfile -t dumps < <(find "$keys_dir" -maxdepth 1 -type f -name 'keys-*.sql.zst.gpg' -printf '%f\n' | sort)
  count=${#dumps[@]}
  for ((i = 0; i < count - 1; i++)); do
    [[ ${dumps[i]} < $cutoff ]] || break
    rm -f "$keys_dir/${dumps[i]}"
  done
  find "$keys_dir" -maxdepth 1 -type f -name '*.partial' -mmin +60 -delete
}

keys_once() {
  local name target meta present
  mapfile -t present < <(existing_key_schemas)
  ((${#present[@]} > 0)) || return 0
  name=keys-$(date -u +%Y%m%dT%H%M%SZ)
  target=$keys_dir/$name.sql.zst.gpg
  meta=$private_dir/$name.meta
  mariadb-dump --defaults-extra-file="$client_config" --single-transaction --quick --hex-blob --databases "${present[@]}" |
    awk -v meta="$meta" '{ print } { last = $0 } END { print "LAST " last > meta }' |
    encrypt > "$target.partial"
  grep -q '^LAST -- Dump completed' "$meta" || fail "$name is incomplete, it doesn't end with 'Dump completed'"
  rm -f "$meta"
  publish "$target"
  prune_keys
}

dump_once() {
  local name target meta position
  name=dump-$(date -u +%Y%m%dT%H%M%SZ)
  target=$dump_dir/$name.sql.zst.gpg
  meta=$private_dir/$name.meta
  log "Dumping ${schemas[*]} into $name"
  mariadb-dump --defaults-extra-file="$client_config" --single-transaction --master-data=2 --quick --hex-blob --databases "${schemas[@]}" |
    awk -v meta="$meta" '{ print } !found && /^-- CHANGE MASTER TO / { print > meta; found = 1 } { last = $0 } END { print "LAST " last > meta }' |
    encrypt > "$target.partial"
  grep -q '^LAST -- Dump completed' "$meta" || fail "$name is incomplete, it doesn't end with 'Dump completed'"
  position=$(sed -n "s/^-- CHANGE MASTER TO MASTER_LOG_FILE='\([^']*\)', MASTER_LOG_POS=\([0-9]*\);.*/\1 \2/p" "$meta")
  [[ -n $position ]] || fail "$name has no binlog position"
  rm -f "$meta"
  printf '%s\n' "$position" > "$dump_dir/$name.position"
  publish "$target"
  log "Dumped $name ($(stat -c %s "$target") bytes, binlog position $position)"
  prune
}

write_dump_metrics() {
  local dumps key_dumps newest oldest lines=()
  mapfile -t dumps < <(find "$dump_dir" -maxdepth 1 -type f -name 'dump-*.sql.zst.gpg' -printf '%f\n' | sort)
  mapfile -t key_dumps < <(find "$keys_dir" -maxdepth 1 -type f -name 'keys-*.sql.zst.gpg' -printf '%f\n' | sort)
  if ((${#dumps[@]} > 0)); then
    newest=$dump_dir/${dumps[-1]}
    oldest=$dump_dir/${dumps[0]}
    lines+=(
      "bookk_backup_dump_last_success_timestamp_seconds $(stat -c %Y "$newest")"
      "bookk_backup_dump_last_size_bytes $(stat -c %s "$newest")"
      "bookk_backup_oldest_dump_timestamp_seconds $(stat -c %Y "$oldest")"
      "bookk_backup_dumps ${#dumps[@]}"
    )
  fi
  if ((${#key_dumps[@]} > 0)); then
    lines+=(
      "bookk_backup_keys_last_success_timestamp_seconds $(stat -c %Y "$keys_dir/${key_dumps[-1]}")"
      "bookk_backup_oldest_keys_timestamp_seconds $(stat -c %Y "$keys_dir/${key_dumps[0]}")"
    )
  fi
  ((${#lines[@]} > 0)) || return 0
  write_metrics backup_dump "${lines[@]}" \
    "bookk_backup_stored_bytes{kind=\"dump\"} $(du -sb "$dump_dir" | cut -f1)" \
    "bookk_backup_stored_bytes{kind=\"binlog\"} $(du -sb "$binlog_dir" | cut -f1)" \
    "bookk_backup_stored_bytes{kind=\"keys\"} $(du -sb "$keys_dir" | cut -f1)"
}

dump_due() {
  local newest
  newest=$(newest_file "$dump_dir" 'dump-*.sql.zst.gpg')
  [[ -n $newest ]] || return 0
  [[ ${newest:5:8} < $(date -u +%Y%m%d) ]] && ((10#$(date -u +%H) >= dump_hour_utc))
}

keys_due() {
  local newest
  newest=$(newest_file "$keys_dir" 'keys-*.sql.zst.gpg')
  [[ -n $newest ]] || return 0
  (($(date +%s) - $(stat -c %Y "$keys_dir/$newest") >= rotate_seconds))
}

run_dumps() {
  local next_metrics=0 next_dump_attempt=0
  trap 'exit 0' TERM INT
  while true; do
    if keys_due; then
      if bash "$0" keys-once; then
        next_metrics=0
      else
        log "ERROR The key dump failed, retrying in a minute"
      fi
    fi
    if (($(date +%s) >= next_dump_attempt)) && dump_due; then
      if bash "$0" dump-once; then
        next_metrics=0
      else
        log "ERROR The dump failed, retrying in 10 minutes"
        next_dump_attempt=$(($(date +%s) + 600))
      fi
    fi
    if (($(date +%s) >= next_metrics)); then
      write_dump_metrics
      next_metrics=$(($(date +%s) + rotate_seconds))
    fi
    sleep 60 &
    wait $!
  done
}

prepare
case ${1:-} in
  binlog) run_binlog ;;
  dump) run_dumps ;;
  dump-once) dump_once ;;
  keys-once) keys_once ;;
  *) fail "Usage: $0 binlog|dump|dump-once|keys-once" ;;
esac
