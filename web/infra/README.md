# Hetzner Cloud production deployment

OpenTofu provisions a complete single-node production environment in Hetzner Cloud:

- a `ccx13` dedicated-vCPU server by default, or a `cpx32` shared-vCPU server;
- a stable Primary IPv4 address, cloud firewall, SSH key, optional reverse DNS, and optional public DNS;
- Debian host hardening and Docker installation through first-boot cloud-init;
- versioned, locked remote state in Hetzner Object Storage.

Application deployments transfer immutable, tagged Docker images over restricted SSH. The routing
service and metrics endpoint remain private; only Caddy publishes HTTP and HTTPS.

The application is stateless. Browser-saved profiles stay in each browser and shared profiles are
carried in their route URL, so there is no production database to back up. Caddy's volume contains
regenerable TLS state.

## Instance choice

Both allowed plans have 8 GiB RAM:

| Plan | CPU | Disk | Use when |
| --- | --- | --- | --- |
| `ccx13` (default) | 2 dedicated AMD vCPUs | 80 GB | Production latency consistency matters more than peak burst throughput. |
| `cpx32` | 4 shared AMD vCPUs | 160 GB | Representative load tests show shared CPU contention is acceptable and four burstable vCPUs help. |

The default service ceiling is 6 GiB with a 5 GiB Java heap, leaving roughly 2 GiB for the OS,
Caddy, Docker, and JVM native memory. Treat that as an initial ceiling, not a measured recommendation. Before launch,
record resident memory after world loading and under representative concurrent routes. Reduce the
heap if the host swaps or approaches its memory limit. Both choices are billed separately for IPv4,
and enabling Hetzner backups adds to the listed server price.

Changing `server_type` performs a resize and can cause downtime. Review the plan and schedule it as
a maintenance operation.

## Prerequisites

The operator machine needs OpenTofu 1.10 or later, Docker with BuildKit, `ssh`, `scp`, and `curl`.
Keep this repository beside the routing engine as `shortest-path-web/` and `shortest-path/`.

Create:

- a Hetzner Cloud project and read/write API token;
- a Hetzner Object Storage bucket with versioning enabled and S3 access credentials;
- an Ed25519 or ECDSA SSH key;
- a fixed administrator IPv4 address or trusted CIDR. A single address should use `/32`.

## Remote state

Copy the backend example and edit only the bucket, region, and endpoint:

```sh
cd infra/environments/production
cp state-backend.hcl.example state-backend.hcl
export AWS_ACCESS_KEY_ID='...'
export AWS_SECRET_ACCESS_KEY='...'
tofu init -backend-config=state-backend.hcl
```

`state-backend.hcl` and `terraform.tfvars` are ignored. Do not put credentials in either file:
backend settings are copied into local OpenTofu metadata, and state contains infrastructure data.
The backend uses native S3 lockfiles to prevent concurrent writers. Keep bucket versioning enabled
and periodically test restoring an older state object.

## Provision

Copy and edit the variables example. `ssh_public_key` contains the public key text, not its path.
Leave `deletion_protection = true` unless intentionally decommissioning or rebuilding production.

```sh
cp terraform.tfvars.example terraform.tfvars
export HCLOUD_TOKEN='...'
tofu fmt -check -recursive
tofu validate
tofu plan -out=production.tfplan
tofu apply production.tfplan
tofu output
```

The firewall is attached before the server's first boot. It admits public HTTP/HTTPS and ICMP,
plus SSH only from `admin_ipv4_cidr`; unmatched inbound traffic is denied. Cloud-init creates the
non-root `deploy` account, disables root/password SSH, enables unattended security updates and
fail2ban, applies a matching host firewall, rotates container logs, and installs a systemd recovery
check.

Wait for cloud-init and verify the host before deploying:

```sh
export SERVER_IP="$(tofu output -raw server_ipv4)"
ssh deploy@"$SERVER_IP" 'cloud-init status --wait && docker version'
```

If the administrator public address changes, update and apply `admin_ipv4_cidr` before leaving the
old network. The Hetzner web console remains the recovery route for a bad firewall or SSH key.

When `create_dns_zone = true`, update the domain registrar to the
`authoritative_nameservers` output after apply. For an existing Hetzner DNS zone, set
`create_dns_zone = false` and provide the zone name.

## Deploy and roll back

Point DNS at the `server_ipv4` output before requesting a real certificate. `SITE_ADDRESS` is the
hostname alone, without a scheme or path. Caddy provisions and renews TLS automatically.

```sh
cd ../../..
export SERVER_IP='203.0.113.10'
export SITE_ADDRESS='travel.example.com'
export SSH_IDENTITY_FILE="$HOME/.ssh/id_ed25519"
./deploy/deploy.sh
```

The script builds both images from the checked-out commits, tags them with the web repository commit
by default, streams them to the host, validates the Compose model, starts it, and checks both the
private service and public reverse proxy. Override `IMAGE_TAG` with a release identifier in CI.

For rollback, select a previously retained image tag on the server:

```sh
cd /opt/osrs-travel
sed -i 's/^IMAGE_TAG=.*/IMAGE_TAG=PREVIOUS_TAG/' deployment.env
docker compose --env-file deployment.env -f compose.yaml up --detach
curl --fail https://travel.example.com/api/ready
```

Do not prune images until the rollback window has passed. Hetzner backups add host-level recovery,
but they do not replace tested redeployment. With the stable Primary IP retained, a replacement
server can reuse the same address after an intentional plan and apply.

## Operations

Useful host checks:

```sh
cd /opt/osrs-travel
docker compose --env-file deployment.env -f compose.yaml ps
docker compose --env-file deployment.env -f compose.yaml logs --tail=200
systemctl status osrs-travel-healthcheck.timer
curl --fail http://127.0.0.1/api/ready
```

Container health checks and the systemd timer recover failed processes, but cannot detect a dead
VM, network partition, or regional outage. Configure an external HTTPS uptime check and alert for
`/api/ready` before launch. Keep `/api/metrics` private; add authenticated host-local collection if
application dashboards are introduced later.

Apply infrastructure changes through reviewed plans, never run simultaneous applies, and never
expose port 22 to `0.0.0.0/0`.
