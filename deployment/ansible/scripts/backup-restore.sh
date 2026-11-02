#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

readonly image=mariadb:11.8.3-ubi9
readonly schemas=(user authorization business appointments notifications)

usage() {
  cat >&2 <<'EOF'
Usage:
  backup-restore.sh sql <backup-dir> [--until 'YYYY-MM-DD HH:MM:SS']
      Decrypts the newest dump taken at or before --until (UTC) and the binlogs after it,
      and writes the SQL that restores the five schemas to that moment to stdout.
      Without --until it restores up to the last binlog shipped.
  backup-restore.sh drill <backup-dir> [--until 'YYYY-MM-DD HH:MM:SS']
      Restores the same into a throwaway MariaDB container and prints every table's row count.
EOF
  exit 2
}

log() { printf '%s\n' "$*" >&2; }

fail() {
  log "ERROR $*"
  exit 1
}

[[ $# -ge 2 ]] || usage
readonly command=$1
readonly backup_dir=$2
shift 2
until_time=
while [[ $# -gt 0 ]]; do
  case $1 in
    --until)
      [[ $# -ge 2 ]] || usage
      until_time=$2
      shift 2
      ;;
    *) usage ;;
  esac
done
[[ $command == sql || $command == drill ]] || usage
[[ -d $backup_dir/dump && -d $backup_dir/binlog ]] || fail "$backup_dir has no dump/ and binlog/, fetch the backups first (make fetch-backups)"
command -v gpg > /dev/null || fail "gpg is missing: brew install gnupg pinentry-mac"
command -v docker > /dev/null || fail "docker is missing"

containers=
cleanup() {
  local container
  for container in $containers; do
    docker rm -fv "$container" > /dev/null 2>&1 || true
  done
}
trap cleanup EXIT

dump_limit() {
  local limit
  [[ -n $until_time ]] || {
    printf 'dump-99999999T999999Z\n'
    return
  }
  limit=dump-$(printf '%s' "$until_time" | sed -E 's/^([0-9]{4})-([0-9]{2})-([0-9]{2})[ T]([0-9]{2}):([0-9]{2}):([0-9]{2})$/\1\2\3T\4\5\6Z/')
  [[ $limit =~ ^dump-[0-9]{8}T[0-9]{6}Z$ ]] || fail "--until takes 'YYYY-MM-DD HH:MM:SS' in UTC, got '$until_time'"
  printf '%s\n' "$limit"
}

list_files() {
  local directory=$1 pattern=$2 file
  for file in "$directory"/$pattern; do
    [[ -f $file ]] && printf '%s\n' "${file##*/}"
  done
  return 0
}

successor() {
  local name=$1
  printf '%s.%06d\n' "${name%.*}" "$((10#${name##*.} + 1))"
}

limit=$(dump_limit)
dump=$(list_files "$backup_dir/dump" 'dump-*.sql.zst.gpg' | grep -E '^dump-[0-9]{8}T[0-9]{6}Z\.sql\.zst\.gpg$' | sort | awk -v limit="$limit.sql.zst.gpg" '$0 <= limit' | tail -n 1)
[[ -n $dump ]] || fail "No dump in $backup_dir/dump was taken at or before ${until_time:-now}"
position_file=$backup_dir/dump/${dump%.sql.zst.gpg}.position
[[ -s $position_file ]] || fail "$position_file is missing"
read -r start_file start_position < "$position_file"

binlogs=$(list_files "$backup_dir/binlog" '*.zst.gpg' | sed 's/\.zst\.gpg$//' | sort | awk -v start="$start_file" '$0 >= start')
expected=$start_file
for binlog in $binlogs; do
  [[ $binlog == "$expected" ]] || fail "$expected is missing from $backup_dir/binlog, so the binlogs after $dump can't be replayed past it"
  expected=$(successor "$binlog")
done
if [[ -z $binlogs ]]; then
  log "WARNING No binlog after $dump is stored yet: $start_file is still open on the database, and the binlog service stores it within 5 minutes of the next write. Restoring the dump alone, as of ${dump:5:4}-${dump:9:2}-${dump:11:2} ${dump:14:2}:${dump:16:2}:${dump:18:2} UTC."
fi

helper=bookk-restore-helper-$$
docker run -d --name "$helper" -e TZ=UTC --tmpfs /work:rw,size=8g,mode=1777 --entrypoint sleep "$image" infinity > /dev/null
containers="$containers $helper"

binlog_count=0
for binlog in $binlogs; do
  gpg --quiet --decrypt "$backup_dir/binlog/$binlog.zst.gpg" | docker exec -i "$helper" sh -c "zstd -q -d -c > /work/$binlog"
  binlog_count=$((binlog_count + 1))
done
[[ $binlog_count -eq 0 ]] || log "Restoring from $dump, then $binlog_count binlog(s) from $start_file at $start_position${until_time:+ up to $until_time UTC}"

emit_sql() {
  gpg --quiet --decrypt "$backup_dir/dump/$dump" | docker exec -i "$helper" zstd -q -d -c
  [[ $binlog_count -eq 0 ]] || docker exec "$helper" sh -c 'mariadb-binlog --start-position="$1" ${2:+--stop-datetime="$2"} /work/*' sh "$start_position" "$until_time"
}

if [[ $command == sql ]]; then
  emit_sql
  exit 0
fi

target=bookk-restore-drill-$$
docker run -d --name "$target" -e MARIADB_ROOT_PASSWORD=drill -e TZ=UTC "$image" > /dev/null
containers="$containers $target"
for attempt in $(seq 60); do
  docker exec "$target" healthcheck.sh --connect --innodb_initialized > /dev/null 2>&1 && break
  [[ $attempt -lt 60 ]] || fail "The drill database didn't start"
  sleep 2
done

emit_sql | docker exec -i "$target" mariadb --binary-mode -uroot -pdrill

schema_list=$(printf "'%s'," "${schemas[@]}")
count_query=$(docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "
  SET SESSION group_concat_max_len = 1048576;
  SELECT GROUP_CONCAT(CONCAT('SELECT ''', table_schema, '.', table_name, ''', COUNT(*) FROM \`', table_schema, '\`.\`', table_name, '\`') ORDER BY table_schema, table_name SEPARATOR ' UNION ALL ')
  FROM information_schema.tables WHERE table_schema IN (${schema_list%,}) AND table_type = 'BASE TABLE'")
docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "$count_query" | column -t
log "Drill restored ${until_time:-the latest stored point} successfully"
