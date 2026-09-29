variable "server_name" {
  description = "Unique hostname for the production server and its related resources."
  type        = string
  default     = "osrs-travel-production"
}

variable "server_type" {
  description = "Hetzner Cloud plan: cpx32 has four shared vCPUs; ccx13 has two dedicated vCPUs. Both have 8 GiB RAM."
  type        = string
  default     = "ccx13"

  validation {
    condition     = contains(["cpx32", "ccx13"], var.server_type)
    error_message = "server_type must be cpx32 or ccx13."
  }
}

variable "location" {
  description = "Hetzner Cloud location, for example fsn1, nbg1, or hel1."
  type        = string
  default     = "fsn1"
}

variable "image" {
  description = "Hetzner Cloud Debian image used to create the server."
  type        = string
  default     = "debian-13"
}

variable "admin_user" {
  description = "Non-root account created by cloud-init for deployments."
  type        = string
  default     = "deploy"

  validation {
    condition     = can(regex("^[a-z_][a-z0-9_-]*$", var.admin_user))
    error_message = "admin_user must be a valid lowercase Linux account name."
  }
}

variable "ssh_key_name" {
  description = "Name assigned to the managed Hetzner Cloud SSH key."
  type        = string
  default     = "osrs-travel-production"
}

variable "ssh_public_key" {
  description = "OpenSSH public key installed for root during creation and for the deployment account by cloud-init."
  type        = string

  validation {
    condition     = can(regex("^(ssh-ed25519|sk-ssh-ed25519@openssh.com|ecdsa-sha2-nistp(256|384|521)) ", trimspace(var.ssh_public_key)))
    error_message = "ssh_public_key must be an Ed25519, security-key Ed25519, or ECDSA OpenSSH public key."
  }
}

variable "admin_ipv4_cidr" {
  description = "The only public IPv4 CIDR allowed to reach SSH, normally a single /32 address."
  type        = string

  validation {
    condition     = can(cidrhost(var.admin_ipv4_cidr, 0)) && !strcontains(var.admin_ipv4_cidr, ":")
    error_message = "admin_ipv4_cidr must be an IPv4 CIDR and should normally be a single /32 address."
  }
}

variable "enable_backups" {
  description = "Enable Hetzner's automatic server backups. This adds to the server cost."
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Protect the production server, disk, and primary IPv4 address from accidental deletion or rebuild."
  type        = bool
  default     = true
}

variable "ptr_hostname" {
  description = "Optional fully-qualified hostname for the server's IPv4 PTR record."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.ptr_hostname == null || can(regex("^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.?$", var.ptr_hostname))
    error_message = "ptr_hostname must be a fully-qualified DNS hostname."
  }
}

variable "manage_dns" {
  description = "Manage the public A record with the Hetzner Cloud provider."
  type        = bool
  default     = false
}

variable "create_dns_zone" {
  description = "Create the DNS zone. Leave false when the zone already exists in the Hetzner project."
  type        = bool
  default     = false
}

variable "dns_zone_name" {
  description = "DNS zone name, such as osrs.travel. Required when manage_dns is true."
  type        = string
  default     = null
  nullable    = true
}

variable "dns_record_name" {
  description = "Record name within the zone, such as @ or travel."
  type        = string
  default     = "@"
}

variable "dns_ttl" {
  description = "TTL for the public A record."
  type        = number
  default     = 300

  validation {
    condition     = var.dns_ttl >= 60 && var.dns_ttl <= 86400
    error_message = "dns_ttl must be between 60 and 86400 seconds."
  }
}
