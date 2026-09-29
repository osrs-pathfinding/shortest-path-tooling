locals {
  labels = {
    application = "osrs-travel"
    environment = "production"
    managed-by  = "opentofu"
  }
}

data "hcloud_server_type" "production" {
  name = var.server_type
}

resource "hcloud_ssh_key" "production" {
  name       = var.ssh_key_name
  public_key = trimspace(var.ssh_public_key)
  labels     = local.labels
}

resource "hcloud_primary_ip" "production" {
  name              = "${var.server_name}-ipv4"
  location          = var.location
  type              = "ipv4"
  auto_delete       = false
  delete_protection = var.deletion_protection
  labels            = local.labels
}

resource "hcloud_firewall" "production" {
  name   = "${var.server_name}-firewall"
  labels = local.labels

  rule {
    direction   = "in"
    protocol    = "tcp"
    port        = "22"
    source_ips  = [var.admin_ipv4_cidr]
    description = "SSH from the administrator network"
  }

  rule {
    direction   = "in"
    protocol    = "tcp"
    port        = "80"
    source_ips  = ["0.0.0.0/0", "::/0"]
    description = "HTTP and ACME redirect"
  }

  rule {
    direction   = "in"
    protocol    = "tcp"
    port        = "443"
    source_ips  = ["0.0.0.0/0", "::/0"]
    description = "HTTPS"
  }

  rule {
    direction   = "in"
    protocol    = "udp"
    port        = "443"
    source_ips  = ["0.0.0.0/0", "::/0"]
    description = "HTTP/3"
  }

  rule {
    direction   = "in"
    protocol    = "icmp"
    source_ips  = ["0.0.0.0/0", "::/0"]
    description = "Path MTU discovery and diagnostics"
  }
}

resource "hcloud_server" "production" {
  name                     = var.server_name
  image                    = var.image
  server_type              = data.hcloud_server_type.production.name
  location                 = var.location
  backups                  = var.enable_backups
  firewall_ids             = [hcloud_firewall.production.id]
  ssh_keys                 = [hcloud_ssh_key.production.id]
  delete_protection        = var.deletion_protection
  rebuild_protection       = var.deletion_protection
  shutdown_before_deletion = true
  labels                   = local.labels

  # The firewall is attached before first boot. Cloud-init then applies the
  # matching host policy and creates the non-root deployment account.
  user_data = join("\n", [
    "#!/usr/bin/env bash",
    "export ADMIN_USER=${jsonencode(var.admin_user)}",
    "export ADMIN_IPV4_CIDR=${jsonencode(var.admin_ipv4_cidr)}",
    "export ADMIN_PUBLIC_KEY=${jsonencode(trimspace(var.ssh_public_key))}",
    replace(file("${path.module}/../../../deploy/host-bootstrap.sh"), "#!/usr/bin/env bash", ""),
  ])

  public_net {
    ipv4_enabled = true
    ipv4         = hcloud_primary_ip.production.id
    ipv6_enabled = true
  }

  lifecycle {
    precondition {
      condition     = data.hcloud_server_type.production.architecture == "x86"
      error_message = "The production images are currently built for x86; choose an x86 server type."
    }
  }
}

resource "hcloud_rdns" "production" {
  count      = var.ptr_hostname == null ? 0 : 1
  server_id  = hcloud_server.production.id
  ip_address = hcloud_primary_ip.production.ip_address
  dns_ptr    = trimsuffix(var.ptr_hostname, ".")
}

resource "hcloud_zone" "production" {
  count             = var.manage_dns && var.create_dns_zone ? 1 : 0
  name              = var.dns_zone_name
  mode              = "primary"
  ttl               = var.dns_ttl
  delete_protection = true
  labels            = local.labels

  lifecycle {
    precondition {
      condition     = var.dns_zone_name != null
      error_message = "dns_zone_name is required when creating a DNS zone."
    }
  }
}

resource "hcloud_zone_rrset" "production_a" {
  count = var.manage_dns ? 1 : 0
  zone  = var.create_dns_zone ? hcloud_zone.production[0].name : var.dns_zone_name
  name  = var.dns_record_name
  type  = "A"
  ttl   = var.dns_ttl
  records = [{
    value   = hcloud_primary_ip.production.ip_address
    comment = "OSRS Travel production server"
  }]
  labels = local.labels

  lifecycle {
    precondition {
      condition     = var.dns_zone_name != null
      error_message = "dns_zone_name is required when manage_dns is true."
    }
  }
}

check "ssh_is_restricted" {
  assert {
    condition     = var.admin_ipv4_cidr != "0.0.0.0/0"
    error_message = "Refusing to expose SSH to the entire internet. Use an administrator /32 or trusted CIDR."
  }
}
