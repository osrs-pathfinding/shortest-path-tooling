terraform {
  required_version = ">= 1.10.0"

  required_providers {
    hcloud = {
      source  = "hetznercloud/hcloud"
      version = "~> 1.69.0"
    }
  }

  # Supply the Hetzner Object Storage settings at init time:
  # tofu init -backend-config=state-backend.hcl
  backend "s3" {}
}

provider "hcloud" {
  # HCLOUD_TOKEN is read from the environment.
}
