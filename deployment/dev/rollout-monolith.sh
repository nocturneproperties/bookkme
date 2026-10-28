#!/usr/bin/env sh
docker context use default || exit
IMAGE_PLATFORM="linux/$(docker version --format '{{.Server.Arch}}')" || exit
export NGINX_CONF=nginx-monolith.conf
docker compose -f environment-compose.yml up -d
cd ../..
#Sometimes gradle daemon cannot resolve docker from ENV, daemon restart helps
./gradlew --stop

cd deployment/dev/service || exit
export $(grep -v '^#' monolith.env | xargs) || exit
cd ../../.. || exit
./gradlew publishImageToLocalRegistry --no-configuration-cache -Djib.from.platforms="$IMAGE_PLATFORM" || exit

cd deployment/dev || exit
docker compose -f monolith-compose.yml up -d
docker container prune -f
docker image prune -f
