# Variables for active-active deployment
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

variable "last_four_digits" {
  type      = string
  sensitive = true
  description = "The last four credit card digits"
}

variable "gcloud_username" {
  type      = string
  description = "The GCP username"  
}

variable "gcloud_region" {
  type      = string
  description = "The GCP region"    
}

variable "gcloud_zone" {
  type      = string
  description = "The GCP zone"    
}

variable "ssh_key_file" {
  type      = string
  description = "The path to the SSH key file"  
}

# Active-Active specific variables
variable "aa_sub_name" {
  type      = string
  description = "The name of the Active-Active subscription"
}

variable "aa_db_name" {
  type      = string
  description = "The name of the Active-Active database"
}

variable "aa_db_password" {
  type      = string
  sensitive = true
  description = "The global password for the Active-Active database"
}

variable "aa_gcloud_region" {
  type      = string
  description = "The secondary GCP region for Active-Active"    
}