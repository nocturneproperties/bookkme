set -eu

rendered=/etc/nginx/conf.d
previous=/tmp/conf.d.previous

rm -rf "$previous"
cp -a "$rendered" "$previous"
/docker-entrypoint.d/20-envsubst-on-templates.sh

if ! nginx -t; then
  rm -rf "$rendered"
  cp -a "$previous" "$rendered"
  echo "nginx -t rejected the new configuration; the previous one is back in $rendered and still running" >&2
  exit 1
fi

nginx -s reload
echo "nginx reloaded"
