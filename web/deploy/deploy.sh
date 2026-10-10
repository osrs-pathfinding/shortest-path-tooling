#!/usr/bin/env bash
set -euo pipefail

: "${SERVER_IP:?Set SERVER_IP from the OpenTofu server_ipv4 output}"

DEPLOY_USER=${DEPLOY_USER:-deploy}
SITE_ADDRESS=${SITE_ADDRESS:-:80}
SERVICE_HEAP_MIN=${SERVICE_HEAP_MIN:-1g}
SERVICE_HEAP_MAX=${SERVICE_HEAP_MAX:-5g}
SERVICE_MEMORY_LIMIT=${SERVICE_MEMORY_LIMIT:-6g}
SERVICE_CPU_LIMIT=${SERVICE_CPU_LIMIT:-2}
SSH_IDENTITY_FILE=${SSH_IDENTITY_FILE:-}
IMAGE_TAG=${IMAGE_TAG:-$(git rev-parse --short=12 HEAD)}

if [[ ! $IMAGE_TAG =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "IMAGE_TAG contains unsafe characters" >&2
  exit 1
fi
if [[ ! -f ../shortest-path/build.gradle ]]; then
  echo "The shortest-path submodule is not checked out; run: git submodule update --init shortest-path" >&2
  exit 1
fi

ssh_options=(-o StrictHostKeyChecking=accept-new)
if [[ -n $SSH_IDENTITY_FILE ]]; then
  ssh_options+=(-i "$SSH_IDENTITY_FILE")
fi
remote="${DEPLOY_USER}@${SERVER_IP}"

echo "Building immutable images for ${IMAGE_TAG}..."
# Both images build from the repository root.
docker build --pull -f Dockerfile -t "osrs-travel-web:${IMAGE_TAG}" ..
docker build --pull -f service/Dockerfile --build-arg "RUNELITE_VERSION=${RUNELITE_VERSION:-latest.release}" \
  -t "osrs-travel-service:${IMAGE_TAG}" ..

echo "Transferring images to ${remote}..."
docker save "osrs-travel-web:${IMAGE_TAG}" "osrs-travel-service:${IMAGE_TAG}" | gzip -1 | \
  ssh "${ssh_options[@]}" "$remote" 'gunzip | docker load'

deployment_dir=$(mktemp -d)
trap 'rm -rf "$deployment_dir"' EXIT
cp deploy/compose.production.yaml "$deployment_dir/compose.yaml"
{
  printf 'IMAGE_TAG=%s\n' "$IMAGE_TAG"
  printf 'SITE_ADDRESS=%s\n' "$SITE_ADDRESS"
  printf 'SERVICE_HEAP_MIN=%s\n' "$SERVICE_HEAP_MIN"
  printf 'SERVICE_HEAP_MAX=%s\n' "$SERVICE_HEAP_MAX"
  printf 'SERVICE_MEMORY_LIMIT=%s\n' "$SERVICE_MEMORY_LIMIT"
  printf 'SERVICE_CPU_LIMIT=%s\n' "$SERVICE_CPU_LIMIT"
} >"$deployment_dir/deployment.env"

ssh "${ssh_options[@]}" "$remote" 'mkdir -p /opt/osrs-travel'
scp "${ssh_options[@]}" "$deployment_dir/compose.yaml" "$deployment_dir/deployment.env" "$remote:/opt/osrs-travel/"
ssh "${ssh_options[@]}" "$remote" \
  'cd /opt/osrs-travel && docker compose --env-file deployment.env -f compose.yaml config --quiet && docker compose --env-file deployment.env -f compose.yaml up --detach --remove-orphans'

echo "Waiting for the routing service..."
ssh "${ssh_options[@]}" "$remote" \
  'cd /opt/osrs-travel; for attempt in $(seq 1 60); do if docker compose --env-file deployment.env -f compose.yaml exec -T service curl -fsS http://127.0.0.1:8080/ready >/dev/null; then exit 0; fi; sleep 2; done; docker compose --env-file deployment.env -f compose.yaml logs --tail=100; exit 1'

if [[ $SITE_ADDRESS == :80 ]]; then
  public_url="http://${SERVER_IP}/api/ready"
else
  public_url="https://${SITE_ADDRESS}/api/ready"
fi
curl --fail --silent --show-error --retry 10 --retry-delay 3 "$public_url" >/dev/null
echo "Deployment ${IMAGE_TAG} is healthy at ${public_url}"
