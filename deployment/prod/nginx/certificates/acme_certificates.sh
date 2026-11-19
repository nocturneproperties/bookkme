#!/bin/sh
set -eu

issue() {
  acme.sh --server letsencrypt --issue --dns dns_cf -d "$1" -d "$2" --dnssleep 60 || [ $? -eq 2 ]
}

deploy() {
  DEPLOY_DOCKER_CONTAINER_KEY_FILE="/etc/nginx/ssl/$1/key.pem" \
    DEPLOY_DOCKER_CONTAINER_CERT_FILE="/etc/nginx/ssl/$1/cert.pem" \
    DEPLOY_DOCKER_CONTAINER_CA_FILE="/etc/nginx/ssl/$1/ca.pem" \
    DEPLOY_DOCKER_CONTAINER_FULLCHAIN_FILE="/etc/nginx/ssl/$1/fullchain.pem" \
    acme.sh --deploy -d "$1" --deploy-hook docker
}

acme.sh --server letsencrypt --register-account -m "$ACME_ACCOUNT_EMAIL"

issue "$DOMAIN_NAME" "*.$DOMAIN_NAME"
deploy "$DOMAIN_NAME"

if ! { issue "$SITE_DOMAIN_NAME" "www.$SITE_DOMAIN_NAME" && deploy "$SITE_DOMAIN_NAME"; }; then
  echo "No certificate for $SITE_DOMAIN_NAME, nginx keeps serving its self-signed placeholder" >&2
fi

exec crond -n -s -m off
