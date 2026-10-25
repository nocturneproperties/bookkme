#!/usr/bin/env sh
docker compose -f environment-compose.yml up -d
cd ../..
#Sometimes gradle daemon cannot resolve docker from ENV, daemon restart helps
./gradlew --stop

start_service () {
  cd deployment/dev/service || exit
  export $(grep -v '^#' $1.env | xargs) || exit
  cd ../../.. || exit
  ./gradlew :service:$1:microservice:publishImageToLocalRegistry --no-configuration-cache || exit
}

start_service "auth"
start_service "user"
start_service "business"
start_service "appointments"
start_service "notifications"

docker context use default || exit
cd deployment/dev || exit
docker compose -f microservices-compose.yml up -d
docker container prune -f
docker image prune -f
