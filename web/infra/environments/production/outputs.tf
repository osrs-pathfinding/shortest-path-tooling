output "server_id" {
  value       = hcloud_server.production.id
  description = "Hetzner Cloud server ID."
}

output "server_ipv4" {
  value       = hcloud_primary_ip.production.ip_address
  description = "Stable public IPv4 address used for DNS and deployment."
}

output "server_ipv6" {
  value       = hcloud_server.production.ipv6_address
  description = "First public IPv6 address assigned to the server."
}

output "server_type" {
  value = {
    name     = data.hcloud_server_type.production.name
    cores    = data.hcloud_server_type.production.cores
    memory   = data.hcloud_server_type.production.memory
    disk     = data.hcloud_server_type.production.disk
    cpu_type = data.hcloud_server_type.production.cpu_type
  }
  description = "Resolved compute characteristics for the selected Hetzner Cloud plan."
}

output "authoritative_nameservers" {
  value       = var.manage_dns && var.create_dns_zone ? hcloud_zone.production[0].authoritative_nameservers.assigned : []
  description = "Nameservers to configure at the registrar when this stack creates the zone."
}

output "ssh_command" {
  value       = "ssh ${var.admin_user}@${hcloud_primary_ip.production.ip_address}"
  description = "Command to connect after cloud-init completes."
}
