#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
readonly target="(ansible_user ~ '@' ~ ansible_host ~ ':' ~ backup_host_dir ~ '/keys')"
readonly on_backup_node="(swarm_labels | default({})).get('backup') == 'true'"
source=$(ANSIBLE_LOAD_CALLBACK_PLUGINS=1 ANSIBLE_STDOUT_CALLBACK=ansible.builtin.json \
  ansible 'managers:workers' -m ansible.builtin.debug -a "msg={{ $target if $on_backup_node else '' }}" |
  python3 -c 'import json, sys
hosts = json.load(sys.stdin)["plays"][0]["tasks"][0]["hosts"].values()
print("\n".join(host["msg"] for host in hosts if host.get("msg")))')
[[ $(printf '%s\n' "$source" | grep -c .) == 1 ]] || {
  printf 'ERROR Expected exactly one host labelled backup: "true" in the inventory\n' >&2
  exit 1
}
printf '%s\n' "$source"
