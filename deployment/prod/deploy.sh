#!/usr/bin/env sh

manager_hostname=$2
manager_user=$3
wireguard_admin_password=$4
ssh_name=${5:-id_rsa}

export "$(grep -v '^#' /keys/global/.env | xargs)"

echo "Manager IP: $manager_hostname"
echo "Manager User: $manager_user"
echo "Using ssh key: $ssh_name"
echo "Wireguard password: $wireguard_admin_password"

ssh-add --apple-use-keychain "$HOME/.ssh/$ssh_name"

docker context create swarm-manager --docker "host=ssh://$manager_user@$manager_hostname"
docker context use swarm-manager

docker network create --driver overlay --opt encrypted operational
docker network create --driver overlay --opt encrypted agent_network
docker network create --driver overlay --opt encrypted monitoring

OS_NAME=$(uname -s)

if [ "$OS_NAME" = "Darwin" ]; then
    brew install wireguard
else
    sudo apt install wireguard
fi

password_hash="$(docker run ghcr.io/wg-easy/wg-easy:14@sha256:5f26407fd2ede54df76d63304ef184576a6c1bb73f934a58a11abdd852fab549 wgpw "$wireguard_admin_password")"

export VPN_PASSWORD_HASH="$password_hash"

docker stack deploy vpn --compose-file vpn/vpn-compose.yml
docker stack deploy nginx --compose-file nginx/nginx-compose.yml
docker secret create dozzle-auth orchestrator/dozzle/users.actual.yaml
docker stack deploy orchestrator --compose-file orchestrator/orchestrator-compose.yml
