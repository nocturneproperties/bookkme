#!/bin/sh

FILE_TO_WAIT_FOR="/etc/nginx/ssl/$DOMAIN_NAME/fullchain.pem"
INTERVAL=5 # seconds

echo "Waiting for file: $FILE_TO_WAIT_FOR"

# Loop until the file exists
while [ ! -f "$FILE_TO_WAIT_FOR" ]; do
  echo "File not found yet, sleeping for $INTERVAL seconds..."
  sleep $INTERVAL
done

echo "File found! Proceeding with the script."

SITE_CERT_DIR="/etc/nginx/ssl/$SITE_DOMAIN_NAME"
if [ ! -f "$SITE_CERT_DIR/fullchain.pem" ]; then
  echo "No certificate for $SITE_DOMAIN_NAME yet, serving a self-signed placeholder until acme.sh deploys one."
  openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 30 \
    -subj "/CN=$SITE_DOMAIN_NAME" -keyout "$SITE_CERT_DIR/key.pem" -out "$SITE_CERT_DIR/fullchain.pem"
fi
