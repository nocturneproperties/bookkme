#!/usr/bin/env python3
import argparse
import base64
import json
import re
import secrets
import subprocess
import sys
from pathlib import Path

PLACEHOLDER = "CHANGE_ME"
DEPLOYMENT_DIR = Path(__file__).resolve().parents[2]
PROD_DIR = DEPLOYMENT_DIR / "prod"
INVENTORY = DEPLOYMENT_DIR / "ansible/inventory/prod.yml"
RETIRED_STACK_ENV = ["RUNNER_ACCESS_TOKEN", "MOBILE_RUNNER_ACCESS_TOKEN", "RUNNER_APP_ID", "RUNNER_APP_PRIVATE_KEY", "VPN_SUBNET"]
RETIRED_STACK_ENV_PREFIXES = ["KAFKA_"]
REQUIRED_KEYS = ["vault_vpn_password"]
RETIRED_KEYS = ["vault_service_env"]
RETIRED_SWARM_SECRETS = ["jwt_master_key", "jwt_quote_key"]
PROMETHEUS_USER = "admin"


def generated_secrets():
    return {
        "vault_monolith_db_password": secrets.token_urlsafe(32),
        "vault_monolith_db_root_password": secrets.token_urlsafe(32),
        "vault_monolith_db_exporter_password": secrets.token_urlsafe(32),
        "vault_monolith_redis_password": secrets.token_urlsafe(32),
        "vault_swarm_secrets": {
            "mariadb_encryption_key": f"1;{secrets.token_hex(32)}\n",
            "signing_key_encryption_key": base64.b64encode(secrets.token_bytes(32)).decode(),
            "firebase_key": None,
        },
    }


def prometheus_web_config(password):
    import bcrypt

    password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    return f"basic_auth_users:\n  {PROMETHEUS_USER}: {password_hash}\n"


def add_prometheus_credentials(swarm_secrets):
    if swarm_secrets.get("prometheus_password") in (None, PLACEHOLDER):
        swarm_secrets["prometheus_password"] = secrets.token_urlsafe(32)
        swarm_secrets.pop("prometheus_web_config", None)
    if swarm_secrets.get("prometheus_web_config") in (None, PLACEHOLDER):
        swarm_secrets["prometheus_web_config"] = prometheus_web_config(swarm_secrets["prometheus_password"])


def read_env_file(path):
    if not path.exists():
        return None
    entries = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        entries[name.strip()] = value
    return entries


def read_text(path):
    return path.read_text() if path.exists() else None


def swarm_manager_host():
    try:
        endpoint = subprocess.run(
            ["docker", "context", "inspect", "swarm-manager", "--format", "{{.Endpoints.docker.Host}}"],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None
    return endpoint.split("@")[-1] if endpoint.startswith("ssh://") else None


def inventory_hosts():
    return list(dict.fromkeys(re.findall(r"vault_hosts\['([^']+)'\]", INVENTORY.read_text())))


def is_retired_stack_env(name):
    return name in RETIRED_STACK_ENV or any(name.startswith(prefix) for prefix in RETIRED_STACK_ENV_PREFIXES)


def normalize(vault):
    for key in RETIRED_KEYS:
        vault.pop(key, None)
    for key in REQUIRED_KEYS:
        vault.setdefault(key, None)
    hosts = vault.get("vault_hosts") or {}
    for host in inventory_hosts():
        hosts.setdefault(host, None)
    vault["vault_hosts"] = hosts
    vault["vault_stack_env"] = {name: value for name, value in (vault.get("vault_stack_env") or {}).items() if not is_retired_stack_env(name)}
    swarm_secrets = {name: value for name, value in (vault.get("vault_swarm_secrets") or {}).items() if name not in RETIRED_SWARM_SECRETS}
    for name, value in generated_secrets().items():
        if isinstance(value, dict):
            for secret, secret_value in value.items():
                if swarm_secrets.get(secret) in (None, PLACEHOLDER):
                    swarm_secrets[secret] = secret_value
        elif vault.get(name) in (None, PLACEHOLDER):
            vault[name] = value
    add_prometheus_credentials(swarm_secrets)
    vault["vault_swarm_secrets"] = swarm_secrets
    return vault


def import_from_files():
    stack_env = read_env_file(PROD_DIR / "keys/global/.env")
    return {
        "vault_hosts": {"manager-1": swarm_manager_host()},
        "vault_registry_username": None,
        "vault_registry_password": None,
        "vault_stack_env": stack_env or {},
        "vault_dozzle_users": read_text(PROD_DIR / "orchestrator/dozzle/users.actual.yaml"),
        "vault_alertmanager_config": read_text(PROD_DIR / "monitoring/alertmanager/config.yml"),
    }


def as_text(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    return value if isinstance(value, str) else str(value)


def scalar(value):
    if value is None:
        return "!unsafe " + json.dumps(PLACEHOLDER)
    value = as_text(value)
    if "\n" in value:
        return None
    return "!unsafe " + json.dumps(value)


def emit(lines, indent, name, value):
    pad = " " * indent
    if isinstance(value, dict):
        if not value:
            lines.append(f"{pad}{name}: {{}}")
            return
        lines.append(f"{pad}{name}:")
        for key, child in value.items():
            emit(lines, indent + 2, key, child)
        return
    if isinstance(value, list):
        if not value:
            lines.append(f"{pad}{name}: []")
            return
        lines.append(f"{pad}{name}:")
        lines.extend(f"{pad}  - {scalar(item)}" for item in value)
        return
    single_line = scalar(value)
    if single_line is not None:
        lines.append(f"{pad}{name}: {single_line}")
        return
    value = as_text(value)
    body = value.rstrip("\n")
    trailing_newlines = len(value) - len(body)
    chomping = {0: "-", 1: ""}.get(trailing_newlines, "+")
    indentation = "2" if body[:1] in (" ", "\t") else ""
    lines.append(f"{pad}{name}: !unsafe |{indentation}{chomping}")
    lines.extend(f"{pad}  {line}" if line else "" for line in body.split("\n"))
    lines.extend("" for _ in range(trailing_newlines - 1))


def placeholders(value, path=""):
    if isinstance(value, dict):
        return [found for key, child in value.items() for found in placeholders(child, f"{path}.{key}" if path else key)]
    return [path] if value in (None, PLACEHOLDER) else []


def load_vault_yaml(stream):
    import yaml

    class Loader(yaml.SafeLoader):
        pass

    Loader.add_constructor("!unsafe", lambda loader, node: loader.construct_scalar(node))
    return yaml.load(stream, Loader=Loader) or {}


def main():
    parser = argparse.ArgumentParser(description="Print a vault YAML to stdout, for 'ansible-vault encrypt'.")
    parser.add_argument("--update", action="store_true", help="read an existing decrypted vault from stdin and bring it up to date")
    args = parser.parse_args()

    vault = normalize(load_vault_yaml(sys.stdin) if args.update else import_from_files())

    lines = []
    for name, value in vault.items():
        emit(lines, 0, name, value)
    print("\n".join(lines))

    missing = placeholders(vault)
    if missing:
        print("Left as CHANGE_ME, fill in with 'make vault-edit':", file=sys.stderr)
        for item in missing:
            print(f"  - {item}", file=sys.stderr)


if __name__ == "__main__":
    main()
