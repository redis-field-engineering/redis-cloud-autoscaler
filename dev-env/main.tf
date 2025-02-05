terraform {
  required_providers {
    rediscloud = {
      source = "RedisLabs/rediscloud"
      version = "2.0.0"
    }
  }
}

provider "rediscloud" {
  # Configuration options
}

provider "google" {
    project = var.gcp_project
    region  = "us-east1"  
}

resource "google_compute_network" "autoscale_test_vpc" {
  name="autoscale-test-vpc"
    auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "autoscale_test_subnet" {
    name          = "autoscale-test-subnet"
    region        = "us-east1"
    ip_cidr_range = "10.0.0.0/24"
    network       = google_compute_network.autoscale_test_vpc.id
    private_ip_google_access = true
    
}


resource "google_compute_firewall" "autoscale_allow_ssh" {
    name    = "autoscale-allow-ssh"
    network = google_compute_network.autoscale_test_vpc.id
    
    allow {
        protocol = "tcp"
        ports    = ["22","80","443","8001", "8443", "8070", "8071", "8080", "9090", "9091", "9093", "9100", "9115", "9443", "10000-19999"]
    }
    
    source_ranges = ["0.0.0.0/0"]  
}

resource "google_compute_firewall" "autoscale_allow_icmp" {
    name    = "autoscale-allow-icmp"
    network = google_compute_network.autoscale_test_vpc.id

    allow {
        protocol = "icmp"
    }

    source_ranges = ["0.0.0.0/0"]
}


resource "google_compute_firewall" "autoscale_allow_dns" {
    name    = "autoscale-allow-dns"
    network = google_compute_network.autoscale_test_vpc.id
    
    allow {
        protocol = "udp"
        ports    = ["53","5353"]
    }
    
    source_ranges = ["0.0.0.0/0"]
  
}

resource "google_compute_firewall" "autoscale_allow_egress" {
    name    = "autoscale-allow-egress"
    network = google_compute_network.autoscale_test_vpc.id

    allow {
        protocol = "all"
    }

    direction     = "EGRESS"
    destination_ranges = ["0.0.0.0/0"]
}


resource "google_compute_instance" "auto_scale_test_vm" {
  name         = "autoscale-test-vm"
  machine_type = "n1-standard-1"
  zone         = "us-east1-b"
  boot_disk {
    initialize_params {
      image = "ubuntu-2004-focal-v20240731"
      size = 50
    }

  }
  network_interface {
    network = google_compute_network.autoscale_test_vpc.id
    subnetwork = google_compute_subnetwork.autoscale_test_subnet.id

    access_config{}
  }

  
  
}

data "rediscloud_payment_method" "card"{
    card_type = "Visa"
    last_four_numbers = var.last_four_digits
}

resource "rediscloud_subscription" "autoscaling_sub" {
    name = "Autoscaling Subscription"
    payment_method_id = data.rediscloud_payment_method.card.id
    memory_storage = "ram"

    cloud_provider {
      provider = "GCP"
      region {
        region = "us-east1"
        multiple_availability_zones = false
        networking_deployment_cidr = "10.0.1.0/24"
      }
    }

    maintenance_windows {
      mode = "automatic"
    }

    creation_plan {
      memory_limit_in_gb = 5
      quantity = 3
      replication = false
      throughput_measurement_by = "operations-per-second"
      throughput_measurement_value = 25000
    }
}

resource "rediscloud_subscription_peering" "autoscale-sub-vpc-peering" {
    subscription_id = rediscloud_subscription.autoscaling_sub.id
    provider_name="GCP"
    gcp_project_id = var.gcp_project
    gcp_network_name = google_compute_network.autoscale_test_vpc.name   
}

resource "google_compute_network_peering" "autoscale-gcp-vpc-peering" {
    name = "autoscale-gcp-vpc-peering"
    network = google_compute_network.autoscale_test_vpc.id
    peer_network = "https://www.googleapis.com/compute/v1/projects/${rediscloud_subscription_peering.autoscale-sub-vpc-peering.gcp_redis_project_id}/global/networks/${rediscloud_subscription_peering.autoscale-sub-vpc-peering.gcp_redis_network_name}"  
}

resource "rediscloud_subscription_database" "autoscale-database" {
    subscription_id = rediscloud_subscription.autoscaling_sub.id
    name = "autoscale-database"
    throughput_measurement_by = "operations-per-second"
    throughput_measurement_value = 1000
    memory_limit_in_gb = 0.5
    modules = [
        {
            "name" : "RedisJSON"
        },
        {
            "name":"RediSearch"
        }
    ]  
}