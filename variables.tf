variable "gcp_project" {
  type      = string
  description = "The GCP project ID"  
}

variable "dns-zone-name" {
  type      = string
  sensitive = true
  description = "The DNS Zone to deploy the app to"  
}

variable "subdomain" {
  type      = string
  sensitive = true
  description = "The subdomain to deploy the app to"
  
}

variable "redis_cloud_account_key" {
  type      = string
  sensitive = true
  description = "The Redis Cloud account key"  
}

variable "redis_cloud_api_key" {
  type      = string
  sensitive = true
  description = "The Redis Cloud API key"  
  
}