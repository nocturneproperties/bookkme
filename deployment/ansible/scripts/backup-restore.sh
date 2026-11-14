#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

readonly image=mariadb:11.8.3-ubi9
readonly flyway_image=flyway/flyway:13.1.0-alpine
readonly schemas=(user authorization business appointments notifications)
readonly keyed_schemas=(user business appointments notifications)
readonly ssh_command=${BACKUP_RESTORE_SSH:-ssh}
repo_root=$(cd "$(dirname "$0")/../../.." && pwd)
readonly repo_root

usage() {
  cat >&2 <<'EOF'
Usage:
  backup-restore.sh sql <backup-dir> (--keys-from <ssh-host>:<dir> | --without-keys)
                        [--until 'YYYY-MM-DD HH:MM:SS' [--without-erasures]]
      Decrypts the newest dump taken at or before --until (UTC) and the binlogs after it,
      and writes the SQL that restores the five schemas to that moment to stdout.
      Without --until it restores up to the last binlog shipped.
      --keys-from streams the newest key dump from the backup node over SSH and restores the
      per-person keys first; --without-keys leaves the key schemas as they are.
      Every subject in the restored shred ledgers gets its key destroyed again at the end.
      With --until it first restores the latest point to read every recorded account and
      client deletion and every shredded key, and appends them, so the app deletes again at
      its next start what the restore brought back. --without-erasures skips that, for
      binlogs that can't be replayed to the end.
  backup-restore.sh drill <backup-dir> (--keys-from <ssh-host>:<dir> | --without-keys)
                          [--until 'YYYY-MM-DD HH:MM:SS' [--without-erasures]]
      Restores the same into a throwaway MariaDB container and prints every table's row count,
      the live and shredded keys per schema, and with --until how many accounts and clients
      the app would delete again.
  backup-restore.sh erasures <backup-dir>
      Restores the latest point into a throwaway MariaDB container and writes the SQL that
      asks the app to delete again every recorded deletion a restored database lacks, and
      that adds every shredded key to the restored shred ledgers.
  backup-restore.sh rehearse <backup-dir> [--until 'YYYY-MM-DD HH:MM:SS']
      Restores the same into a throwaway MariaDB container, runs this checkout's Flyway migrations
      on every schema, and prints the tables whose row count they changed.
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
with_erasures=yes
keys_from=
without_keys=
while [[ $# -gt 0 ]]; do
  case $1 in
    --keys-from)
      [[ $# -ge 2 && $2 == *:* ]] || usage
      keys_from=$2
      shift 2
      ;;
    --without-keys)
      without_keys=yes
      shift
      ;;
    --until)
      [[ $# -ge 2 ]] || usage
      until_time=$2
      shift 2
      ;;
    --without-erasures)
      with_erasures=
      shift
      ;;
    *) usage ;;
  esac
done
[[ $command == sql || $command == drill || $command == rehearse || $command == erasures ]] || usage
[[ $command != erasures || -z $until_time ]] || usage
if [[ $command == sql || $command == drill ]]; then
  [[ -n $keys_from && -z $without_keys || -z $keys_from && -n $without_keys ]] || usage
else
  [[ -z $keys_from && -z $without_keys ]] || usage
fi
[[ -d $backup_dir/dump && -d $backup_dir/binlog ]] || fail "$backup_dir has no dump/ and binlog/, fetch the backups first (make fetch-backups)"
command -v gpg > /dev/null || fail "gpg is missing: brew install gnupg pinentry-mac"
command -v docker > /dev/null || fail "docker is missing"

migration_dir() { printf '%s/service/%s/data/src/main/resources/db/migration/%s\n' "$repo_root" "$1" "$1"; }

if [[ $command == rehearse ]]; then
  for schema in "${schemas[@]}"; do
    [[ -d $(migration_dir "$schema") ]] || fail "$(migration_dir "$schema") is missing, rehearse runs from a full checkout of the version to deploy"
  done
fi

erasure_sql=
if [[ ($command == sql || $command == drill) && -n $until_time && -n $with_erasures ]]; then
  log "Restoring the latest point first, to find the account and client deletions a restore to $until_time UTC would undo"
  erasure_sql=$("$BASH" "$0" erasures "$backup_dir") ||
    fail "The latest point couldn't be restored, so the deletions made after $until_time can't be found. Restore it with --without-erasures and delete them again by hand (Restoring prod in deployment/ansible/README.md)"
fi

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

key_dump=
keys_host=${keys_from%%:*}
keys_dir=${keys_from#*:}
if [[ -n $keys_from ]]; then
  key_dump=$("$ssh_command" "$keys_host" "ls -1 -- '$keys_dir'" | grep -E '^keys-[0-9]{8}T[0-9]{6}Z\.sql\.zst\.gpg$' | sort | tail -n 1) || true
  [[ -n $key_dump ]] || fail "No key dump in $keys_from. Without one the restore has no keys and every encrypted field reads as missing; restore with --without-keys only when the database's own key schemas survived"
  log "Restoring the keys from $key_dump, taken ${key_dump:5:4}-${key_dump:9:2}-${key_dump:11:2} ${key_dump:14:2}:${key_dump:16:2}:${key_dump:18:2} UTC. Keys created after it are lost"
fi

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
docker run -d --name "$helper" -e TZ=UTC --tmpfs /work:rw,size=8g,mode=1777 --tmpfs /var/lib/mysql:rw,size=1m --entrypoint sleep "$image" infinity > /dev/null
containers="$containers $helper"

binlog_count=0
for binlog in $binlogs; do
  gpg --quiet --decrypt "$backup_dir/binlog/$binlog.zst.gpg" | docker exec -i "$helper" sh -c "zstd -q -d -c > /work/$binlog"
  binlog_count=$((binlog_count + 1))
done
[[ $binlog_count -eq 0 ]] || log "Restoring from $dump, then $binlog_count binlog(s) from $start_file at $start_position${until_time:+ up to $until_time UTC}"

emit_sql() {
  printf 'CREATE DATABASE IF NOT EXISTS `%s_short_retention`;\n' "${keyed_schemas[@]}"
  [[ -z $key_dump ]] || "$ssh_command" "$keys_host" "cat -- '$keys_dir/$key_dump'" | gpg --quiet --decrypt | docker exec -i "$helper" zstd -q -d -c
  gpg --quiet --decrypt "$backup_dir/dump/$dump" | docker exec -i "$helper" zstd -q -d -c
  [[ $binlog_count -eq 0 ]] || docker exec "$helper" sh -c 'mariadb-binlog --start-position="$1" ${2:+--stop-datetime="$2"} /work/*' sh "$start_position" "$until_time"
}

tombstone_sql() {
  local schema
  printf 'DELIMITER ;\nSET autocommit = 1;\nDELIMITER ;;\n'
  for schema in "${keyed_schemas[@]}"; do
    printf 'BEGIN NOT ATOMIC DECLARE CONTINUE HANDLER FOR 1146 BEGIN END; INSERT INTO `%s_short_retention`.subject_key (subject_id, wrapped_key, master_key_version, created_at, shredded_at) SELECT subject_id, NULL, NULL, shredded_at, shredded_at FROM `%s`.shredded_subject ON DUPLICATE KEY UPDATE wrapped_key = NULL, master_key_version = NULL, shredded_at = COALESCE(subject_key.shredded_at, VALUES(shredded_at)); END;;\n' "$schema" "$schema"
  done
  printf 'DELIMITER ;\n'
}

if [[ $command == sql ]]; then
  emit_sql
  if [[ -n $erasure_sql ]]; then
    printf 'DELIMITER ;\n%s\n' "$erasure_sql"
    log "Appended the $(printf '%s\n' "$erasure_sql" | grep -c 'reapply_requested) VALUES') recorded deletions and $(printf '%s\n' "$erasure_sql" | grep -c 'shredded_subject` (subject_id') shredded keys. The deletions the restore lacks are marked, and the app deletes them again at its next start"
  fi
  tombstone_sql
  log "Every key in the restored shred ledgers is destroyed again at the end of the restore"
  exit 0
fi

target=bookk-restore-drill-$$
docker run -d --name "$target" -e MARIADB_ROOT_PASSWORD=drill -e TZ=UTC --tmpfs /var/lib/mysql:rw,size=8g,uid=999,gid=999,mode=0700 "$image" > /dev/null
containers="$containers $target"
for attempt in $(seq 60); do
  docker exec "$target" healthcheck.sh --connect --innodb_initialized > /dev/null 2>&1 && break
  [[ $attempt -lt 60 ]] || fail "The drill database didn't start"
  sleep 2
done

emit_sql | docker exec -i "$target" mariadb --binary-mode -uroot -pdrill

table_counts() {
  local schema_list count_query
  schema_list=$(printf "'%s'," "${schemas[@]}")
  count_query=$(docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "
    SET SESSION group_concat_max_len = 1048576;
    SELECT GROUP_CONCAT(CONCAT('SELECT ''', table_schema, '.', table_name, ''', COUNT(*) FROM \`', table_schema, '\`.\`', table_name, '\`') ORDER BY table_schema, table_name SEPARATOR ' UNION ALL ')
    FROM information_schema.tables WHERE table_schema IN (${schema_list%,}) AND table_type = 'BASE TABLE'")
  docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "$count_query"
}

table_exists() {
  [[ $(docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$1' AND table_name = '$2'") == 1 ]]
}

create_table_if_missing_sql() {
  local schema=$1 table=$2 qualified ddl
  qualified="\`$schema\`.\`$table\`"
  ddl=$(docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "SHOW CREATE TABLE $qualified" | cut -f2- | sed 's/\\n/ /g')
  printf '%s;\n' "${ddl/CREATE TABLE \`$table\`/CREATE TABLE IF NOT EXISTS $qualified}"
}

recorded_erasures_sql() {
  local schema=$1 table=$2 columns=$3 values=$4 qualified
  qualified="\`$schema\`.\`$table\`"
  table_exists "$schema" "$table" || return 0
  create_table_if_missing_sql "$schema" "$table"
  docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "
    SET time_zone = '+00:00';
    SELECT CONCAT('INSERT IGNORE INTO $qualified ($columns, erased_at, reapply_requested) VALUES (', $values, ', ''', erased_at, ''', TRUE);') FROM $qualified ORDER BY erased_at"
}

shred_ledger_sql() {
  local schema=$1 qualified
  qualified="\`$schema\`.\`shredded_subject\`"
  table_exists "$schema" shredded_subject || return 0
  create_table_if_missing_sql "$schema" shredded_subject
  docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "
    SET time_zone = '+00:00';
    SELECT CONCAT('INSERT IGNORE INTO $qualified (subject_id, shredded_at) VALUES (0x', HEX(subject_id), ', ''', shredded_at, ''');') FROM $qualified ORDER BY shredded_at"
}

key_counts() {
  local schema
  for schema in "${keyed_schemas[@]}"; do
    table_exists "${schema}_short_retention" subject_key || continue
    docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "
      SELECT '${schema}_short_retention', COUNT(*) - COUNT(shredded_at), COUNT(shredded_at) FROM \`${schema}_short_retention\`.subject_key"
  done
}

if [[ $command == erasures ]]; then
  printf "SET autocommit = 1;\nSET time_zone = '+00:00';\n"
  recorded_erasures_sql authorization erased_account 'user_id' "'0x', HEX(user_id)"
  recorded_erasures_sql business erased_client 'client_id, business_id' "'0x', HEX(client_id), ', 0x', HEX(business_id)"
  for schema in "${keyed_schemas[@]}"; do
    shred_ledger_sql "$schema"
  done
  exit 0
fi

if [[ $command == drill ]]; then
  table_counts | column -t
  if [[ -n $erasure_sql ]]; then
    printf '%s\n' "$erasure_sql" | docker exec -i "$target" mariadb --binary-mode -uroot -pdrill
    pending=$(docker exec "$target" mariadb -uroot -pdrill --batch --skip-column-names -e "
      SELECT CONCAT((SELECT COUNT(*) FROM \`authorization\`.erased_account WHERE reapply_requested), ' account(s) and ',
                    (SELECT COUNT(*) FROM business.erased_client WHERE reapply_requested), ' client(s)')")
    log "A restore to $until_time UTC brings back $pending deleted after it. The app deletes them again at its next start"
  fi
  tombstone_sql | docker exec -i "$target" mariadb --binary-mode -uroot -pdrill
  keys=$(key_counts)
  if [[ -n $keys ]]; then
    printf 'KEY_SCHEMA\tLIVE\tSHREDDED\n%s\n' "$keys" | column -t
  else
    log "WARNING The restore holds no keys, so every encrypted field reads as missing"
  fi
  log "Drill restored ${until_time:-the latest stored point} successfully"
  exit 0
fi

counts_before=$(table_counts)
for schema in "${schemas[@]}"; do
  log "Migrating $schema"
  docker run --rm --network "container:$target" -v "$(migration_dir "$schema"):/flyway/migrations:ro" "$flyway_image" \
    -url="jdbc:mariadb://127.0.0.1:3306/$schema" -user=root -password=drill \
    -defaultSchema="$schema" -createSchemas=true -baselineOnMigrate=true \
    -locations=filesystem:/flyway/migrations migrate >&2 ||
    fail "The $schema migrations failed on the backup of ${until_time:-the latest stored point}. Fix the script so it can run again on a half-migrated schema before deploying"
done
counts_after=$(table_counts)

changes=$({
  printf '%s\n' "$counts_before" | sed 's/^/before\t/'
  printf '%s\n' "$counts_after" | sed 's/^/after\t/'
} | awk -F'\t' '
  NF == 3 && $2 !~ /\.flyway_schema_history$/ && $1 == "before" { old[$2] = $3 }
  NF == 3 && $2 !~ /\.flyway_schema_history$/ && $1 == "after" { now[$2] = $3 }
  END {
    for (t in old) if (!(t in now)) print t "\t" old[t] "\tdropped"
    for (t in now) if (!(t in old)) print t "\tnew\t" now[t]
    for (t in now) if ((t in old) && old[t] != now[t]) print t "\t" old[t] "\t" now[t]
  }' | sort)
if [[ -z $changes ]]; then
  log "The migrations ran on the backup of ${until_time:-the latest stored point} without changing any table's row count"
  exit 0
fi
printf 'TABLE\tBEFORE\tAFTER\n%s\n' "$changes" | column -t
log "The migrations ran on the backup of ${until_time:-the latest stored point}. Every row count above must have changed on purpose"
