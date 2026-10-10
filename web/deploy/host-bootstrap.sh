#!/usr/bin/env bash
set -euo pipefail

: "${ADMIN_PUBLIC_KEY:?ADMIN_PUBLIC_KEY is required}"
: "${ADMIN_IPV4_CIDR:?ADMIN_IPV4_CIDR is required}"

ADMIN_USER=${ADMIN_USER:-deploy}

if [[ $EUID -ne 0 ]]; then
  echo "host-bootstrap.sh must run as root" >&2
  exit 1
fi

. /etc/os-release
if [[ ${ID:-} != debian ]]; then
  echo "This bootstrap supports a Hetzner Debian Cloud host; found ${ID:-unknown}." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --yes ca-certificates curl fail2ban nftables unattended-upgrades ufw

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: ${VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
apt-get update
apt-get install --yes docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

if ! id "$ADMIN_USER" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "$ADMIN_USER"
fi
usermod -aG docker "$ADMIN_USER"
install -d -m 0700 -o "$ADMIN_USER" -g "$ADMIN_USER" "/home/${ADMIN_USER}/.ssh"
printf '%s\n' "$ADMIN_PUBLIC_KEY" >"/home/${ADMIN_USER}/.ssh/authorized_keys"
chown "$ADMIN_USER:$ADMIN_USER" "/home/${ADMIN_USER}/.ssh/authorized_keys"
chmod 0600 "/home/${ADMIN_USER}/.ssh/authorized_keys"

install -d -m 0750 -o "$ADMIN_USER" -g "$ADMIN_USER" /opt/osrs-travel
install -d -m 0755 /etc/docker
cat >/etc/docker/daemon.json <<'EOF'
{
  "live-restore": true,
  "log-driver": "local",
  "log-opts": {
    "max-size": "20m",
    "max-file": "5"
  },
  "no-new-privileges": true
}
EOF
systemctl enable --now docker
systemctl restart docker

cat >/etc/sysctl.d/90-osrs-travel.conf <<'EOF'
net.ipv4.conf.all.rp_filter=1
net.ipv4.conf.default.rp_filter=1
net.ipv4.tcp_syncookies=1
net.ipv4.conf.all.accept_redirects=0
net.ipv4.conf.default.accept_redirects=0
net.ipv6.conf.all.accept_redirects=0
net.ipv6.conf.default.accept_redirects=0
EOF
sysctl --system >/dev/null

ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow from "$ADMIN_IPV4_CIDR" to any port 22 proto tcp comment 'restricted SSH'
ufw allow 80/tcp comment 'HTTP ACME and redirect'
ufw allow 443/tcp comment 'HTTPS'
ufw allow 443/udp comment 'HTTP/3'
ufw --force enable

cat >/etc/ssh/sshd_config.d/90-osrs-travel.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
X11Forwarding no
AllowAgentForwarding no
MaxAuthTries 3
EOF
sshd -t

cat >/etc/fail2ban/jail.d/sshd.local <<'EOF'
[sshd]
enabled = true
bantime = 1h
findtime = 10m
maxretry = 5
EOF
systemctl enable --now fail2ban
systemctl enable --now unattended-upgrades

cat >/usr/local/sbin/osrs-travel-healthcheck <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd /opt/osrs-travel
[[ -f compose.yaml && -f deployment.env ]] || exit 0
if ! docker compose --env-file deployment.env -f compose.yaml exec -T web wget -q -O /dev/null http://127.0.0.1:2019/config/ || \
  ! docker compose --env-file deployment.env -f compose.yaml exec -T service curl -fsS http://127.0.0.1:8080/ready >/dev/null; then
  docker compose --env-file deployment.env -f compose.yaml up --detach --remove-orphans
fi
EOF
chmod 0755 /usr/local/sbin/osrs-travel-healthcheck

cat >/etc/systemd/system/osrs-travel-healthcheck.service <<'EOF'
[Unit]
Description=Recover unhealthy OSRS Travel containers
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/osrs-travel-healthcheck
EOF

cat >/etc/systemd/system/osrs-travel-healthcheck.timer <<'EOF'
[Unit]
Description=Check OSRS Travel containers every minute

[Timer]
OnBootSec=2min
OnUnitActiveSec=1min
RandomizedDelaySec=10s
Persistent=true

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now osrs-travel-healthcheck.timer

# Restart SSH last, after the deploy user and firewall rule are in place.
systemctl restart ssh
echo "Host bootstrap complete. Verify a second SSH session as ${ADMIN_USER} before closing the root session."
