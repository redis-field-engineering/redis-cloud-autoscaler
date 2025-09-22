terraform {
  required_providers {
    rediscloud = {
      source = "RedisLabs/rediscloud"
      version = "2.0.0"
    }
  }
}

provider "rediscloud" {
    secret_key = var.redis_cloud_api_key
    api_key = var.redis_cloud_account_key
}

provider "google" {
    project = var.gcp_project
    region  = var.gcloud_region
}


resource "google_compute_network" "autoscale_test_vpc" {
    name="autoscale-test-vpc"
    auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "autoscale_test_subnet" {
    name          = "autoscale-test-subnet"
    region        = var.gcloud_region
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


data "rediscloud_payment_method" "card"{
    card_type = "Mastercard"
    last_four_numbers = var.last_four_digits
}

resource "rediscloud_subscription" "autoscaling_sub" {
    name = var.sub_name
    payment_method_id = data.rediscloud_payment_method.card.id
    memory_storage = "ram"

    cloud_provider {
        provider = "GCP"
        region {
            region = var.gcloud_region
            multiple_availability_zones = false
            networking_deployment_cidr = "10.0.1.0/24"
        }
    }

    maintenance_windows {
        mode = "automatic"
    }

    creation_plan {
        memory_limit_in_gb = 5
        quantity = 1
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
    name = var.db_name
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

locals {
  private_endpoint_host = join("", slice(split(":", rediscloud_subscription_database.autoscale-database.private_endpoint), 0, 1))
}

resource "google_compute_instance" "autoscaler-vm"{
    name = "autoscaler-vm"
    machine_type = "n1-standard-1"
    zone = var.gcloud_zone
    boot_disk {
        initialize_params {
            image = "ubuntu-2004-focal-v20240731"
            size = 50
        }
    }

    network_interface {
        network = google_compute_network.autoscale_test_vpc.id
        subnetwork = google_compute_subnetwork.autoscale_test_subnet.id
        access_config {          
        }
    }
}


resource "null_resource" "build_app" {
    depends_on = [ google_compute_instance.autoscaler-vm ]

    connection {
        type = "ssh"
        host = google_compute_instance.autoscaler-vm.network_interface[0].access_config[0].nat_ip
        user = "${var.gcloud_username}"
        private_key = file(var.ssh_key_file)
    }

    provisioner "file" {
        source = "../docker"
        destination = "."      
    }
     
    provisioner "remote-exec" {
        inline = [
            "echo 'REDIS_HOST_AND_PORT=${rediscloud_subscription_database.autoscale-database.private_endpoint}' | sudo tee -a docker/.env > /dev/null",
            "echo 'REDIS_PASSWORD=${rediscloud_subscription_database.autoscale-database.password}' | sudo tee -a docker/.env > /dev/null",
            "echo 'REDIS_CLOUD_API_KEY=${var.redis_cloud_api_key}' | sudo tee -a docker/.env > /dev/null",
            "echo 'REDIS_CLOUD_ACCOUNT_KEY=${var.redis_cloud_account_key}' | sudo tee -a docker/.env > /dev/null",
            "echo 'REDIS_CLOUD_SUBSCRIPTION_ID=${rediscloud_subscription.autoscaling_sub.id}' | sudo tee -a docker/.env > /dev/null",
            "echo 'ALERT_MANAGER_HOST=alertmanager' | sudo tee -a docker/.env > /dev/null",
            "echo 'REDIS_CLOUD_INTERNAL_ENDPOINT=${local.private_endpoint_host}' | sudo tee -a docker/.env > /dev/null",
            "echo 'REDIS_CLOUD_AUTOSCALER_HOST=autoscaler' | sudo tee -a docker/.env > /dev/null",
        ]      
    }

    provisioner "remote-exec" {
      inline = [ 
        "sudo apt-get update",
        "sudo apt-get install -y git docker.io docker-compose",
        "sudo mkdir -p /usr/local/lib/docker/cli-plugins",
        "sudo curl -SL https://github.com/docker/compose/releases/download/v2.22.0/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose",
        "sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose",
        "sudo systemctl start docker",
        "sudo systemctl enable docker",
        "sudo usermod -aG docker $USER",
        "export $(grep -v '^#' ./docker/.env | xargs)",
        "envsubst < ./docker/prometheus/prometheus.template.yml > ./docker/prometheus/prometheus.yml",
        "sudo docker compose -f ./docker/docker-compose.yml up -d",
       ]
      
    }

    ## install java 17
}

resource "google_dns_record_set" "autoscaler_dns" {
    managed_zone = var.dns-zone-name
    name = "autoscaler.${var.subdomain}."
    type = "A"
    ttl = 300
    rrdatas = [google_compute_instance.autoscaler-vm.network_interface[0].access_config[0].nat_ip]  
}
