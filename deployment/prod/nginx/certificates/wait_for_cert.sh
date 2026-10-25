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