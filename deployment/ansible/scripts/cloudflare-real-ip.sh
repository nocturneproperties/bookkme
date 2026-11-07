#!/usr/bin/env bash
set -euo pipefail

target="$(cd "$(dirname "$0")/../../prod/nginx/snippets" && pwd)/cloudflare-real-ip.conf"
draft=$(mktemp)
trap 'rm -f "$draft"' EXIT

{
  echo "real_ip_header CF-Connecting-IP;"
  for list in ips-v4 ips-v6; do
    { curl -fsS "https://www.cloudflare.com/$list"; echo; } | tr -s '[:space:]' '\n' | sed '/^$/d; s/.*/set_real_ip_from &;/'
  done
} >"$draft"

grep -q set_real_ip_from "$draft"
mv "$draft" "$target"
chmod 0644 "$target"
trap - EXIT
echo "wrote $target"
