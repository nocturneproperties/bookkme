#!/usr/bin/env sh
docker context use default || exit
IMAGE_PLATFORM="linux/$(docker version --format '{{.Server.Arch}}')" || exit
export NGINX_CONF=nginx.conf
docker compose --profile kafka -f environment-compose.yml up -d
cd ../..
#Sometimes gradle daemon cannot resolve docker from ENV, daemon restart helps
./gradlew --stop

start_service () {
  cd deployment/dev/service || exit
  export $(grep -v '^#' $1.env | xargs) || exit
  cd ../../.. || exit
  ./gradlew :service:$1:microservice:publishImageToLocalRegistry --no-configuration-cache -Djib.from.platforms="$IMAGE_PLATFORM" || exit
}

start_service "auth"
start_service "user"
start_service "business"
start_service "appointments"
start_service "notifications"

cd deployment/dev || exit
docker compose -f microservices-compose.yml up -d
docker container prune -f
docker image prune -f
