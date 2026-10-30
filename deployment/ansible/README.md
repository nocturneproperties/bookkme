# Ansible deployment

Runs from your machine (or CI) over SSH against the prod swarm. Nothing is installed on the servers besides Docker and the Python libraries the Docker modules need.

Prod runs the **monolith** (`MonolithServer`, events in-process, no Kafka) as one stack: the app, one MariaDB holding all five schemas, and one Redis. The app answers on the network aliases `authorization.service`, `user.service`, `business.service`, `appointments.service` and `notifications.service`, so nginx routes `/api/<svc>` to it unchanged.

MariaDB and Valkey are reachable only on the `storage` network, which the app, the two exporters and NocoDB join next to `operational`; nginx, the VPN stack and monitoring can't reach them. Every network in `swarm_networks` is an encrypted overlay (IPsec between nodes), created by the `swarm` role (`make infra tags=swarm`), and must exist before a stack that uses it is deployed. MariaDB also refuses unencrypted connections (`require_secure_transport` in `db/my.cnf`), and the app connects with `sslMode=verify-full`. MariaDB ≥ 11.4 generates a self-signed certificate when none is configured, and MariaDB Connector/J ≥ 3.4 verifies it against the login password (MariaDB's zero-configuration TLS, for `mysql_native_password` or `ed25519` users with a password), so there is no CA or certificate to rotate. Any other database client must turn TLS on itself: `database-exporter` runs with `--tls.insecure-skip-verify`, because its Go driver can't verify the certificate that way, so its connection is encrypted but the certificate isn't checked.

The compose files in `deployment/prod` and `deployment/adminpanel` stay the source of truth: a stack deploy copies the stack's directory to `/opt/bookk/stacks/<stack>/` on the manager, writes its secret files from the vault next to it, and runs `docker stack deploy` there with the stack's variables in the environment. Docker reads the secret files (configs, secrets, `env_file`) only during that deploy and keeps them in the swarm, so they are deleted again right after it, also when it fails, and nothing from the vault stays on the manager's disk; files that are no longer in the repo are removed from the copy before each deploy. The monolith deploy logs out of the image registry afterwards, since `--with-registry-auth` already stored the credentials in the service. The roles install with apt, so a host that is not Debian-based fails the run instead of being skipped. Kafka (`deployment/prod/kafka`) and the per-service stacks (`deployment/prod/microservice`) are not deployed by Ansible.

## One-time setup

```sh
brew install ansible ansible-lint
security add-generic-password -a "$USER" -s bookk-ansible-vault -w "$(openssl rand -base64 32)"
security find-generic-password -s bookk-ansible-vault -w     # save this in your password manager
cd deployment/ansible
make vault-import    # builds the encrypted inventory/group_vars/all/vault.yml and generates every key and password
make vault-edit      # fill in what the import reported as CHANGE_ME
```

`make vault-import` reads `deployment/prod/keys/global/.env`, the dozzle users and alertmanager config, and generates the MariaDB encryption key, the signing-key encryption key, the database/Redis passwords, the Prometheus password, the Dozzle session secret and the NocoDB admin password. `make vault-update` brings an existing vault to the current structure: it generates whatever is missing and never replaces a value that is set.

The vault password is read by `scripts/vault-pass.sh`: `$ANSIBLE_VAULT_PASSWORD` first (CI), then the `bookk-ansible-vault` keychain item, then `~/.bookk-vault-pass`. `vault.example.yml` shows the expected structure. Add every worker to `inventory/prod.yml` and its address to `vault_hosts`; the monolith, its database and Redis are placed on workers. A stack that keeps data on a local volume is pinned to one node by a swarm label, which `make infra tags=swarm` applies from the host's `swarm_labels` (values must be quoted strings):

```yaml
    workers:
      hosts:
        worker-1:
          ansible_host: "{{ vault_hosts['worker-1'] }}"
          swarm_labels:
            database: "true"
            kafka: "true"
```

The monolith's MariaDB and Valkey run only on the node labelled `database: "true"`, Kafka (when deployed) only on the one labelled `kafka: "true"`, Prometheus and Grafana only on the one labelled `monitoring: "true"` (`manager-1`); the app itself runs on any worker. Without the label the task stays pending instead of starting on an empty volume elsewhere, so label exactly one node for each before the first deploy (`make bootstrap` applies the labels before `make deploy`).

Edit the vault with an editor that keeps no history of the decrypted temp file: `EDITOR=nano make vault-edit`, or VS Code with `"workbench.localHistory.exclude": { "**/.ansible/tmp/**": true }`.

**Back up the vault password and `vault_swarm_secrets.mariadb_encryption_key`.** Every table is encrypted with that key; without it the database files and their backups are unreadable.

## First deployment

Publish the image first: bump `version` in the root `build.gradle.kts`, then push a matching tag (`git tag monolith/0.15.2 && git push origin monolith/0.15.2`). `.github/workflows/monolith-publish.yml` pushes `ghcr.io/nocturneproperties/bookkme-server:<version>` and refuses a tag that differs from the project version.

```sh
make check                     # what would change, connects but changes nothing
make bootstrap                 # hosts, swarm, networks, secrets, infra stacks (creates stacks that don't exist yet)
make deploy v=0.15.2 first=1   # the monolith, creating its stack
```

## Commands

| What | Command |
|---|---|
| Preview everything (no changes) | `make check` |
| Preview one stack | `make check tags=nginx` |
| Hosts, swarm, networks, secrets and all infra stacks | `make infra` |
| Only some stacks | `make infra tags=nginx,monitoring` |
| Deploy / roll back the monolith | `make deploy v=0.15.2` |
| Edit secrets | `make vault-edit` |
| Syntax check / lint | `make syntax` / `make lint` |

Tags: `hosts` (Docker and SSH), `ssh` (only the SSH hardening), `swarm`, `vpn`, `nginx`, `orchestrator`, `monitoring`, `admin`.

Every host allows SSH logins by key only: `roles/ssh_hardening` writes `/etc/ssh/sshd_config.d/10-hardening.conf` (`PasswordAuthentication no`, `KbdInteractiveAuthentication no`, `PermitRootLogin prohibit-password`), checks it with `sshd -t`, reloads `ssh` and asserts the effective `sshd -T` values. The file name sorts before Ubuntu's `50-cloud-init.conf`, which turns password logins on, and sshd keeps the first value it reads. Port 22 is open to the internet for the GitHub-hosted deploy job, so this is what keeps it safe.

## Safety rails

- **No accidental duplicate stacks.** A stack deploy fails if no stack of that name is deployed yet, so a wrong name in `inventory/group_vars/all/main.yml` cannot start a second copy of a stack next to the real one. `make bootstrap` and `make deploy … first=1` pass `-e allow_new_stacks=true`.
- **Secrets are only created, never replaced.** The swarm role creates a missing secret from `vault_swarm_secrets` and refuses one still set to `CHANGE_ME`; rotating an existing secret stays a manual `docker secret` operation, because swarm secrets in use cannot be updated in place.
- **`--check` skips stack deploys** (`community.docker.docker_stack` has no check mode); it still shows file changes, network and secret differences.
- **Secret files are never diffed or logged** (`diff: false`, `no_log`). Neither is a stack deploy: `docker_stack`'s result carries every service's spec, vault-built env included, so it runs under `no_log` and a separate assert reports Docker's error message on failure. The deploy workflow writes the SSH key itself (`ANSIBLE_PRIVATE_KEY_FILE`), so no third-party action ever sees it or the vault password.

## VPN clients

wg-easy v14's image points `iptables` at the **legacy** backend, and an nf_tables host (Ubuntu 26.04, kernel 7.0) never loads the legacy `iptable_nat` module, which the container cannot load itself: its default `wg-quick` hooks then fail with ``can't initialize iptables table `nat'``, the container exits and swarm keeps replacing it (the `vpn_clients` role reports `No such container`, since the id it waited for is already gone). `vpn-compose.yml` therefore sets `WG_POST_UP`/`WG_POST_DOWN` to the same rules through the image's `iptables-nft`, which uses the nf_tables modules Docker already loads; keep both hooks in step with each other.

VPN clients resolve through dnsmasq on the manager's `wg0` address, so dnsmasq publishes 53/udp in `mode: host` (only on the manager, not through the ingress routing mesh on every node). Ubuntu's systemd-resolved holds port 53 on `127.0.0.53`/`127.0.0.54`, and Docker then cannot bind `0.0.0.0:53`: an ingress-published 53 got neither its `docker-proxy` socket nor its NAT rules, without an error, so every VPN client's DNS was refused (no domain, and no internet, since all of a client's DNS goes to `.2`). `docker_host` therefore turns off the stub listener on managers (`/etc/systemd/resolved.conf.d/no-stub-listener.conf`) and points `/etc/resolv.conf` at the upstream `/run/systemd/resolve/resolv.conf`.

The wg-easy panel is only reachable from inside the VPN (`https://vpn.<domain>`, nginx allows only the VPN subnet; its port 51821 is not published), so the first client configs come from Ansible: every `make infra tags=vpn` (and `make bootstrap`) creates each client in `vpn_clients` (`inventory/group_vars/all/main.yml`, `admin-1` and `admin-2` by default) that doesn't exist yet and saves all of their configs to `deployment/ansible/vpn-clients/<name>.conf` (gitignored, mode `0600`). It logs in to the panel's API inside the wg-easy container with `vault_vpn_password`, the plain password `VPN_PASSWORD_HASH` was generated from, so the two must match. Existing clients are never changed or removed: delete a client in the panel, and drop it from `vpn_clients` too, or the next run creates it again. Add a name to `vpn_clients` to get a config for another device. A config file holds that client's private key; import it into WireGuard and delete it, rerunning fetches it again.

The manager itself is a VPN client: `vpn_host_client` (`admin-1`) is pinned to `vpn_host_address` (`.2` of the subnet) and brought up on the manager host as `wg0` (`wireguard-tools`, `wg-quick@wg0` enabled at boot, without the config's `DNS` line so the host keeps its own resolver), and the run waits for its handshake. That address is what the VPN's dnsmasq resolves `DOMAIN_NAME` and every subdomain to, and the DNS server wg-easy hands out, so VPN clients reach nginx and dnsmasq through the manager's `wg0`, keeping their VPN source address (wg-easy only masquerades traffic leaving to the internet), which is what nginx's `allow ${VPN_SUBNET}` checks. Its config is therefore not saved locally: importing it on another device would take the manager's address and key. If another client already holds that address, the run stops and names it; move that client to another address in the panel.

The VPN subnet is `172.16.91.0/24`, set once as `vpn_network_prefix` in `inventory/group_vars/all/main.yml` (a `/24`, the only size wg-easy v14 supports): it lies outside Docker's default bridge pools (`172.17–31.x`, `192.168.x`) and swarm's overlay pool (`10.x`), so no Docker network on a node can take it. From it come `vpn_subnet`, `vpn_address_template` (wg-easy's `x` form) and `vpn_host_address`, which reach the stacks as `VPN_SUBNET`, `VPN_ADDRESS_TEMPLATE` and `VPN_HOST_ADDRESS` (`vpn_stack_env`, merged into every stack's environment): wg-easy's `WG_DEFAULT_ADDRESS`/`WG_ALLOWED_IPS`/`WG_DEFAULT_DNS`, dnsmasq's `host-record` and nginx's `allow` lines. wg-easy stores its server address in its volume on first start and ignores `WG_DEFAULT_ADDRESS` afterwards, so when the stored subnet differs from `vpn_network_prefix` the run renumbers the install in place: backup through the panel API, the server and every client moved to the new prefix keeping their host number (`10.8.0.5` becomes `172.16.91.5`) and their keys, restore, and `wg-quick` restarted inside the container. The manager's `wg0` and the saved configs follow on the same run; every other device must re-download its config from the panel, since a client's address is part of its config. Changing the prefix again later works the same way, and needs `make infra tags=vpn,nginx`.

## Monitoring

Prometheus finds its targets through swarm DNS, one target per task: `tasks.node-exporter` and `tasks.cadvisor` (global services, one task per node, reached on the `monitoring` network only) and `tasks.monolith_monolith` (the monolith stack's service, across stacks on `operational`, port 80, `/metrics`), plus `tasks.monolith_database-exporter` (job `mariadb`), `tasks.monolith_redis-exporter` (job `valkey`) and `tasks.loki`. A service's alias (`exporter:9100` before) is its virtual IP, which balances every scrape to a random task: with several nodes the series of all of them mixed under one target, and during a monolith deploy two processes' counters alternated. node-exporter takes its node's hostname (`hostname: "{{.Node.Hostname}}"`), and the node recording rules join `node_uname_info` on `instance` for a `nodename` label, which the alerts print, since `instance` is now a task IP that changes when the task restarts.

Prometheus keeps 30 days or 5GB, whichever is reached first (~7k series measured on one node of the test stack, about 2–3GB for 30 days at these scrape intervals). Memory limits, from idle usage measured on a test swarm with several times that as headroom: Prometheus 768M (57M idle; it sets `GOMEMLIMIT` to 90% of the limit by itself), Grafana 512M (188M), Loki 512M (70M), Alloy 256M (46M), cAdvisor 256M (23M), Alertmanager 128M (12M), node-exporter 64M (9M). `ContainerHighMemoryUsage` warns at 80% of a limit.

MariaDB and Valkey are watched by two exporters in the monolith stack. `redis-exporter` logs in with the Valkey password (Valkey has a single user) and skips config metrics (`REDIS_EXPORTER_CONFIG_COMMAND=-`, since `CONFIG` is renamed away). `database-exporter` logs in as its own MariaDB user `exporter`, which holds only `PROCESS` (server status, no table data) and at most 3 connections, with `vault_monolith_db_exporter_password`. `init-schemas.sh` only runs on an empty volume and root can only log in from inside the database container, so the user comes from MariaDB's `--init-file`: the monolith deploy renders `db/users-init.sql` from `roles/monolith/templates/users-init.sql.j2` into the swarm secret `monolith-mariadb-users-init-v<n>`, and MariaDB runs it (`CREATE USER IF NOT EXISTS`, `ALTER USER`, `GRANT`) on every start. The same file creates `nocodb_reader` for NocoDB: `SELECT` on the five schemas only, at most 20 connections, with `vault_monolith_db_nocodb_password`. Adding or renaming that secret restarts the database once, on the first deploy that carries it (a few seconds without database); changing a password or a grant needs the secret name bumped and restarts it again.

Alerts (`prometheus/node_alerts.yml`): node CPU, memory and disk; container memory and CPU throttling; `ContainerRestarting` when a container of a service started in the last 15 minutes and stopped again (a crash, an OOM kill, a failed deploy; two deploys within 15 minutes also count the replaced container); `TargetDown` for any scrape target silent for 2 minutes; `MariaDBDown`, `MariaDBConnectionsHigh` (80% of `max_connections`), `ValkeyDown`, `ValkeyMemoryHigh` (80% of `--maxmemory`, which under `noeviction` is where writes start failing); `LogStorageGrowing`.

Logs: Alloy (a global service, so one per node) reads every swarm container's output through the node's Docker socket and pushes it to Loki (`monitoring` network only, not routed by nginx, no auth) with the labels `stack`, `service` (the compose service name, `monolith` rather than `monolith_monolith`) and `node`. For the monolith it joins a stack trace to the line it belongs to (a new entry starts with the logback timestamp), drops the per-scrape `[200 OK], GET /metrics` lines, and stores the logback level as `level` structured metadata; for every other service Loki detects the level itself. Either way queries filter on `detected_level`. Loki keeps 14 days (the compactor deletes older chunks). It cannot delete by size, so `LogStorageGrowing` fires when the last day's stored chunks (`loki_ingester_chunk_size_bytes_sum`, the compressed bytes written, which matched the chunk files on disk byte for byte) would add up to more than 5GB over 14 days; the Logs dashboard shows the same projection. Shorten `retention_period` or find the noisy service then. Alloy keeps its read positions in the node-local `alloy_data` volume, so a restart resumes where it stopped. Grafana logs at `warn`: at `info` it logged every data-source query, so each dashboard refresh filled the logs with its own requests. Dozzle stays for live tailing. Dozzle runs global (one task per node) behind the `dozzle` alias, a virtual IP that sends each request to a random task, so every task must sign logins with the same key: `vault_dozzle_session_secret` is mounted as `/data/session_secret`. Without it each task generates its own key (Dozzle ≥ v11 persists a random one there instead of deriving it from `users.yml`), a session cookie from one task is rejected by the next, and the login page redirects in a loop. The image is pinned (`amir20/dozzle:v11.3.1`), not `latest`, so a redeploy can't pull in a behavior change like that one.

NocoDB (the `admin` stack, `admin.<domain>`, VPN only) creates its super admin on start from `NOCODB_ADMIN_EMAIL` and `NOCODB_ADMIN_PASSWORD` in `vault_stack_env` (`NC_ADMIN_EMAIL`/`NC_ADMIN_PASSWORD`; change both together, NocoDB starts only with a password of 8+ characters with an uppercase letter, a digit and a special character, which `make vault-update` generates). Telemetry is off (`NC_DISABLE_TELE`). The database is added by hand in the UI as an external MariaDB source at `monolith.database:3306` (on the `storage` network) with SSL turned on, since MariaDB refuses plaintext connections, one source per schema, logging in as the read-only `nocodb_reader` (password `vault_monolith_db_nocodb_password`, `make vault-view`), never the app's `monolith` user, whose edits would skip the application's rules and the outbox, which needs `NC_ALLOW_LOCAL_EXTERNAL_DBS=true`: NocoDB refuses sources on private addresses otherwise, and the overlay network is one.

Grafana provisions three dashboards (`deployment/prod/monitoring/grafana/dashboards/`, linked to each other at the top right; Application is the home page): **Application** (requests per second, 4xx/5xx share, p95 response time, a per-route table with average, p95 and slowest, the container's and JVM's memory, CPU, GC and threads, MariaDB and Valkey, and the monolith's warnings and errors with stack traces), **Infrastructure** (CPU, memory, disk and network per node; per swarm service its containers, memory against its limit, CPU and the containers started in the last hour, which grows during a crash loop) and **Logs** (every service's logs, filtered by stack, service, level and a regex). The data sources have fixed uids (`prometheus`, `loki`) the dashboards refer to; `datasources.yml` deletes and recreates the Prometheus one on every start because Grafana refuses to change the uid of an existing data source ("data source not found"), which the old uid-less one was. Each dashboard file is its own swarm config (`grafana_dashboard_<name>-v<n>`), so a changed dashboard needs its name bumped; edits made in the UI are lost when Grafana restarts, so export the JSON into the file instead.

## Prometheus login

Prometheus asks for HTTP basic auth on every endpoint, its own `/metrics`, the API and `/-/reload` included, on top of nginx's VPN-only `prometheus.<domain>`. The user is `admin`, the password `vault_swarm_secrets.prometheus_password` (`make vault-view`). Two swarm secrets carry it, both generated by `import-secrets.py`: `prometheus_password` (the plain password, read by Prometheus's self-scrape as `password_file` and by Grafana's provisioned datasource as `$__file{/run/secrets/prometheus_password}`) and `prometheus_web_config` (the `--web.config.file`, holding its bcrypt hash). `make vault-update` generates the pair when the password is missing, and derives the hash from the stored password when only the hash is missing, so the two always match. The secrets are created by the `swarm` tag, so the first rollout is `make infra tags=swarm,monitoring`. Changing the password follows the manual secret rotation: delete both values from the vault and run `make vault-update`, then on the manager `docker stack rm monitoring` (its volumes stay) and `docker secret rm prometheus_password prometheus_web_config` (swarm refuses to remove a secret in use), and rerun `make infra tags=swarm,monitoring`.

## Swarm configs and secrets from files are immutable

Stacks that mount a config or secret from a file (nginx, vpn, monitoring, orchestrator, monolith) version its name by hand (`nginx-v45`, `monolith-mariadb-init-schemas-v1`). Changing such a file without bumping the name fails the deploy with "only updates to Labels are allowed" — same as with `docker stack deploy` directly.

## From CI

GitHub Actions only publishes images and calls this playbook; it never runs `docker` against the swarm itself.

- `.github/workflows/monolith-publish.yml` runs on a `monolith/<version>` tag: it checks the tag equals `version` in the root `build.gradle.kts`, pushes `ghcr.io/nocturneproperties/bookkme-server:<version>`, then calls the deploy workflow.
- `.github/workflows/microservice-publish.yml` runs on an `auth/`, `user/`, `business/`, `appointment/` or `notification/<version>` tag and only publishes `ghcr.io/nocturneproperties/bookkme-<service>:<version>` (same version check); nothing deploys microservices while prod runs the monolith.
- `.github/workflows/deploy.yml` runs `ansible-playbook playbooks/deploy.yml -e version=<version>` on a GitHub-hosted runner. Run it by hand (**Actions → Deploy monolith → Run workflow**) to redeploy or roll back to any published version.

Every workflow runs on GitHub-hosted runners pinned to `ubuntu-24.04` (`ubuntu-latest` moves to a new Ubuntu on GitHub's schedule); nothing runs inside the swarm. The deploy job therefore reaches the manager over SSH from GitHub's address ranges, which change, so the manager's SSH port must accept connections from the internet (key-only root login) at the `vault_hosts['manager-1']` address. The job installs Ansible with `uv`, pinned to the same versions as a local Homebrew install: ansible-core 2.21.5, community.docker 5.4.0, community.general 13.5.0. It pulls with the registry token from the vault, not `GITHUB_TOKEN`: the deploy passes `--with-registry-auth`, which stores the credentials in the swarm service for later pulls, and `GITHUB_TOKEN` expires when the job ends.

GitHub setup the workflows need:
- Secrets `ANSIBLE_VAULT_PASSWORD` (new), `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS` and `GRADLE_ENCRYPTION_KEY` (already used by the old workflows).
- An environment `production` (**Settings → Environments**) with yourself as required reviewer, so every deploy waits for an approval. Without protection rules GitHub creates it on first use and deploys without asking.

`make infra` / `make bootstrap` are not wired into CI: infrastructure changes are rare and reviewed by hand, so run them from your machine.
