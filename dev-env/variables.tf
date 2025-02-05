variable "last_four_digits" {
  type      = string
  sensitive = true
  description = "The last four credit card digits"
}

variable "gcp_project" {
  type      = string
  description = "The GCP project ID"  
}