terraform {
  required_providers {
    rediscloud = {
      source = "RedisLabs/rediscloud"
      version = "2.0.0"
    }
  }
}

# We'll handle endpoint formatting in the Prometheus configuration directly

provider "rediscloud" {
    secret_key = var.redis_cloud_api_key
    api_key = var.redis_cloud_account_key
}

provider "google" {
    project = var.gcp_project
}

# Create a VPC for our environment
resource "google_compute_network" "aa_autoscale_vpc" {
    name = "aa-autoscale-vpc"
    auto_create_subnetworks = false  
}

# Create subnet in primary region
resource "google_compute_subnetwork" "aa_autoscale_subnet_primary" {
    name = "aa-autoscale-subnet-primary"
    region = var.gcloud_region
    network = google_compute_network.aa_autoscale_vpc.id
    ip_cidr_range = "10.0.0.0/24"
    private_ip_google_access = true  
}

# Create subnet in secondary region
resource "google_compute_subnetwork" "aa_autoscale_subnet_secondary" {
    name = "aa-autoscale-subnet-secondary"
    region = var.aa_gcloud_region
    network = google_compute_network.aa_autoscale_vpc.id
    ip_cidr_range = "10.0.1.0/24"
    private_ip_google_access = true  
}

# Create firewall rules
resource "google_compute_firewall" "aa_firewall_rules" {
    name = "aa-autoscale-firewall"
    network = google_compute_network.aa_autoscale_vpc.id

    allow {
        protocol = "tcp"
        ports = ["22","80","443","8001", "8443", "8070", "8071", "8080", "9090", "9091", "9093", "9100", "9115", "9443", "10000-19999"]
    }

    allow {
        protocol = "icmp"
    }

    allow {
        protocol = "udp"
        ports = ["53","5353"]
    }

    source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "aa_allow_egress" {
    name = "aa-autoscale-egress"
    network = google_compute_network.aa_autoscale_vpc.id
    allow {
      protocol = "all"
    }

    direction = "EGRESS"
    destination_ranges = ["0.0.0.0/0"]
}

# Get payment method
data "rediscloud_payment_method" "aa_card"{
    card_type = "Visa"
    last_four_numbers = var.last_four_digits
}

# Create Active-Active subscription
resource "rediscloud_active_active_subscription" "aa_subscription" {
    name = var.aa_sub_name
    payment_method_id = data.rediscloud_payment_method.aa_card.id
    cloud_provider = "GCP"

    creation_plan {
      dataset_size_in_gb = 1
      quantity = 1
      modules = ["RediSearch", "RedisJSON"]
      region {
        region = var.gcloud_region
        networking_deployment_cidr = "10.0.2.0/24"
        write_operations_per_second = 1000
        read_operations_per_second = 1000
      }
      region {
        region = var.aa_gcloud_region
        networking_deployment_cidr = "10.0.3.0/24"
        write_operations_per_second = 1000
        read_operations_per_second = 1000
      }
    }
}

# Create the database in the Active-Active subscription
resource "rediscloud_active_active_subscription_database" "aa_database" {
    name = var.aa_db_name
    subscription_id = rediscloud_active_active_subscription.aa_subscription.id
    dataset_size_in_gb = 1
    global_password = var.aa_db_password
    global_modules = ["RediSearch", "RedisJSON"]
}

# Setup peering for primary region
resource "rediscloud_active_active_subscription_peering" "aa_peering_primary" {
    subscription_id = rediscloud_active_active_subscription.aa_subscription.id
    provider_name = "GCP"
    gcp_project_id = var.gcp_project
    gcp_network_name = google_compute_network.aa_autoscale_vpc.name  
    source_region = var.gcloud_region
}

resource "google_compute_network_peering" "aa_gcp_peering_primary" {
    name = "aa-gcp-peering-primary"
    network = google_compute_network.aa_autoscale_vpc.self_link
    peer_network = "https://www.googleapis.com/compute/v1/projects/${rediscloud_active_active_subscription_peering.aa_peering_primary.gcp_redis_project_id}/global/networks/${rediscloud_active_active_subscription_peering.aa_peering_primary.gcp_redis_network_name}"
}

# Setup peering for secondary region
resource "rediscloud_active_active_subscription_peering" "aa_peering_secondary" {
    subscription_id = rediscloud_active_active_subscription.aa_subscription.id
    provider_name = "GCP"
    gcp_project_id = var.gcp_project
    gcp_network_name = google_compute_network.aa_autoscale_vpc.name  
    source_region = var.aa_gcloud_region
}

resource "google_compute_network_peering" "aa_gcp_peering_secondary" {
    name = "aa-gcp-peering-secondary"
    network = google_compute_network.aa_autoscale_vpc.self_link
    peer_network = "https://www.googleapis.com/compute/v1/projects/${rediscloud_active_active_subscription_peering.aa_peering_secondary.gcp_redis_project_id}/global/networks/${rediscloud_active_active_subscription_peering.aa_peering_secondary.gcp_redis_network_name}"
}

locals {
  matches       = regex("^.*?\\.(internal\\..*):\\d+", rediscloud_active_active_subscription_database.aa_database.private_endpoint[var.gcloud_region])
  private_endpoint_host = "${local.matches[0]}"
}

locals {
    matches_secondary = regex("^.*?\\.(internal\\..*):\\d+", rediscloud_active_active_subscription_database.aa_database.private_endpoint[var.aa_gcloud_region])
  private_endpoint_secondary_host = "${local.matches_secondary[0]}"
}


# Create Prometheus VM
resource "google_compute_instance" "aa_prometheus_vm" {
    name = "aa-prometheus-vm"
    machine_type = "n1-standard-1"
    zone = var.gcloud_zone
    boot_disk {
        initialize_params {
            image = "ubuntu-2004-focal-v20240731"
            size = 50
        }
    }

    network_interface {
        network = google_compute_network.aa_autoscale_vpc.id
        subnetwork = google_compute_subnetwork.aa_autoscale_subnet_primary.id
        access_config {          
        }
    }
  
    metadata_startup_script = <<-EOT
    #!/bin/bash
    apt-get update
    apt-get install -y wget tar
    useradd --no-create-home --shell /bin/false prometheus
    mkdir /etc/prometheus
    mkdir /var/lib/prometheus
    wget https://github.com/prometheus/prometheus/releases/download/v2.42.0/prometheus-2.42.0.linux-amd64.tar.gz
    
    tar -xvzf prometheus-2.42.0.linux-amd64.tar.gz
    mv prometheus-2.42.0.linux-amd64/prometheus /usr/local/bin/
    mv prometheus-2.42.0.linux-amd64/promtool /usr/local/bin/
    mv prometheus-2.42.0.linux-amd64/consoles /etc/prometheus
    mv prometheus-2.42.0.linux-amd64/console_libraries /etc/prometheus

    wget https://github.com/prometheus/alertmanager/releases/download/v0.28.0/alertmanager-0.28.0.linux-amd64.tar.gz
    tar -xvzf alertmanager-0.28.0.linux-amd64.tar.gz
    mv alertmanager-0.28.0.linux-amd64/alertmanager /usr/local/bin/
    mv alertmanager-0.28.0.linux-amd64/amtool /usr/local/bin/

    # Create the prometheus.yml with scrape configs
    cat <<EOF > /etc/prometheus/prometheus.yml
    global:
      scrape_interval: 15s
      evaluation_interval: 15s
    rule_files:
        - /etc/prometheus/alert.rules
    alerting:
      alertmanagers:
        - static_configs:
          - targets:
            # Alertmanager's default port is 9093
            - localhost:9093

    scrape_configs:
      - job_name: 'prometheus'
        static_configs:
          - targets: ['localhost:9090']

      - job_name: 'rediscloud-primary'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /
        scheme: https
        static_configs:
          - targets: ['${local.private_endpoint_host}:8070']
      - job_name: 'rediscloud-primary-v2'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /v2
        scheme: https
        static_configs:
          - targets: ['${local.private_endpoint_host}:8070']
      - job_name: 'rediscloud-secondary'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /
        scheme: https
        static_configs:
          - targets: ['${local.private_endpoint_secondary_host}:8070']
      - job_name: 'rediscloud-secondary-v2'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /v2
        scheme: https
        static_configs:
          - targets: ['${local.private_endpoint_secondary_host}:8070']
      - job_name: 'autoscaler-prometheus-actuator'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /actuator/prometheus
        scheme: http
        static_configs:
          - targets: ['${google_compute_instance.aa_autoscaler_vm.network_interface[0].access_config[0].nat_ip}:8080']
    EOF

    cat <<EOF > /etc/prometheus/alert.rules
    groups:
        - name: RedisAlerts
          rules:
            - alert: IncreaseMemory
              expr: sum by (instance, db) (redis_server_used_memory) / sum by (instance, db) (redis_server_maxmemory) * 100 > 80
              for: 1m
              labels:
                severity: warning
              annotations:
                summary: "High Redis Memory Usage"
                description: "Redis memory usage is high"
            - alert: DecreaseMemory
              expr: sum by (instance, db) (redis_server_used_memory) / sum by (instance, db) (redis_server_maxmemory) * 100 < 20
              for: 1m
              labels:
                severity: warning
              annotations:
                summary: "Low Redis Memory Usage"
                description: "Redis memory usage is low"
            - alert: IncreaseThroughput
              expr: sum by (db, instance) (irate(endpoint_write_requests[1m]) + irate(endpoint_read_requests[1m])) / on(db) group_left() redis_db_configured_throughput * 100 > 80
              for: 1m
              labels:
                severity: warning
              annotations:
                summary: "High Redis Throughput"
                description: "Redis throughput is high"
            - alert: DecreaseThroughput
              expr: sum by (db, instance) (irate(endpoint_write_requests[1m]) + irate(endpoint_read_requests[1m])) / on(db) group_left() redis_db_configured_throughput * 100 < 20
              for: 1m
              labels:
                severity: warning
              annotations:
                summary: "Low Redis Throughput"
                description: "Redis throughput is low"
    EOF

    cat <<EOF > /etc/prometheus/alertmanager.yml
    global:
      resolve_timeout: 1m
    route:
        receiver: webhook-receiver
        repeat_interval: 1m
    receivers:
      - name: webhook-receiver
        webhook_configs:
          - url: 'http://${google_compute_instance.aa_autoscaler_vm.network_interface[0].access_config[0].nat_ip}:8080/alerts'
    EOF

    chown -R prometheus:prometheus /etc/prometheus /var/lib/prometheus

    sudo mkdir /alertmanager/data -p
    sudo chown -R prometheus:prometheus /alertmanager /alertmanager/data

    echo '[Unit]
    Description=Prometheus
    Wants=network-online.target
    After=network-online.target

    [Service]
    User=prometheus
    Group=prometheus
    Type=simple
    ExecStart=/usr/local/bin/prometheus --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.path=/var/lib/prometheus/

    [Install]
    WantedBy=multi-user.target' > /etc/systemd/system/prometheus.service

    echo '[Unit]
    Description=Alertmanager
    Wants=network-online.target
    After=network-online.target

    [Service]
    User=prometheus
    Group=prometheus
    Type=simple
    ExecStart=/usr/local/bin/alertmanager --config.file=/etc/prometheus/alertmanager.yml
    WorkingDirectory=/alertmanager

    [Install]
    WantedBy=multi-user.target' > /etc/systemd/system/alertmanager.service
    

    systemctl daemon-reload
    systemctl enable prometheus
    systemctl start prometheus
    systemctl enable alertmanager
    systemctl start alertmanager
  EOT
}

# Create autoscaler VM
resource "google_compute_instance" "aa_autoscaler_vm" {
    name = "aa-autoscaler-vm"
    machine_type = "n1-standard-1"
    zone = var.gcloud_zone
    boot_disk {
        initialize_params {
            image = "ubuntu-2004-focal-v20240731"
            size = 50
        }
    }

    network_interface {
        network = google_compute_network.aa_autoscale_vpc.id
        subnetwork = google_compute_subnetwork.aa_autoscale_subnet_primary.id
        access_config {          
        }
    }
}

# Build the autoscaler jar and deploy it to the VM
resource "null_resource" "aa_build_app" {
    depends_on = [google_compute_instance.aa_autoscaler_vm]
    
    # Build the jar locally
    provisioner "local-exec" {
        command = "./gradlew clean bootjar"
        working_dir = "${path.module}/.."
    }

    # Upload and configure the jar on the VM
    connection {
        type = "ssh"
        host = google_compute_instance.aa_autoscaler_vm.network_interface[0].access_config[0].nat_ip
        user = var.gcloud_username
        private_key = file(var.ssh_key_file)
    }

    provisioner "file" {
        source = "../autoscaler/redis-cloud-autoscaler/build/libs/redis-cloud-autoscaler-0.0.4.jar"
        destination = "autoscaler.jar"      
    }

    # Install Java and set up the systemd service
    provisioner "remote-exec" {
        inline = [
            # Install Java
            "sudo apt-get update",
            "sudo apt-get install -y openjdk-17-jdk",

            # Create autoscaler user
            "sudo useradd -r -s /bin/false autoscaler",
            
            # Copy the JAR file to the common directory
            "sudo cp ~/autoscaler.jar /usr/local/bin/autoscaler.jar",
            "sudo chown autoscaler:autoscaler /usr/local/bin/autoscaler.jar",
            "sudo chmod 755 /usr/local/bin/autoscaler.jar",
            
            # Create the systemd service file
            "echo '[Unit]' | sudo tee /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Description=Autoscaler Service' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'After=network.target' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo '' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo '[Service]' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_HOST_AND_PORT=${rediscloud_active_active_subscription_database.aa_database.private_endpoint[var.gcloud_region]}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_PASSWORD=${rediscloud_active_active_subscription_database.aa_database.global_password}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_CLOUD_API_KEY=${var.redis_cloud_api_key}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_CLOUD_ACCOUNT_KEY=${var.redis_cloud_account_key}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_CLOUD_SUBSCRIPTION_ID=${rediscloud_active_active_subscription.aa_subscription.id}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=ALERT_MANAGER_HOST=${google_compute_instance.aa_prometheus_vm.network_interface[0].access_config[0].nat_ip}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=ALERT_MANAGER_PORT=9093' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'ExecStart=/usr/bin/java -jar /usr/local/bin/autoscaler.jar' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Restart=always' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'User=autoscaler' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'StandardOutput=journal' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'StandardError=journal' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo '' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo '[Install]' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'WantedBy=multi-user.target' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",

            # Reload systemd and enable the service
            "sudo systemctl daemon-reload",
            "sudo systemctl enable autoscaler.service",
            "sudo systemctl start autoscaler.service"
        ]      
    }
}

# DNS records
resource "google_dns_record_set" "aa_autoscaler_dns" {
    managed_zone = var.dns-zone-name
    name = "aa-autoscaler.${var.subdomain}."
    type = "A"
    ttl = 300
    rrdatas = [google_compute_instance.aa_autoscaler_vm.network_interface[0].access_config[0].nat_ip]  
}

resource "google_dns_record_set" "aa_prometheus_dns" {
    managed_zone = var.dns-zone-name
    name = "prometheus.aa-autoscaler.${var.subdomain}."
    type = "A"
    ttl = 300
    rrdatas = [google_compute_instance.aa_prometheus_vm.network_interface[0].access_config[0].nat_ip]  
}

# Output the endpoints for reference
output "primary_region_public_endpoint" {
    value = rediscloud_active_active_subscription_database.aa_database.public_endpoint[var.gcloud_region]
}

output "primary_region_private_endpoint" {
    value = rediscloud_active_active_subscription_database.aa_database.private_endpoint[var.gcloud_region]
}

output "secondary_region_public_endpoint" {
    value = rediscloud_active_active_subscription_database.aa_database.public_endpoint[var.aa_gcloud_region]
}

output "secondary_region_private_endpoint" {
    value = rediscloud_active_active_subscription_database.aa_database.private_endpoint[var.aa_gcloud_region]
}

output "autoscaler_vm_ip" {
    value = google_compute_instance.aa_autoscaler_vm.network_interface[0].access_config[0].nat_ip
}

output "prometheus_vm_ip" {
    value = google_compute_instance.aa_prometheus_vm.network_interface[0].access_config[0].nat_ip
}

output "autoscaler_dns" {
    value = nonsensitive(google_dns_record_set.aa_autoscaler_dns.name)
}

output "prometheus_dns" {
    value = nonsensitive(google_dns_record_set.aa_prometheus_dns.name)
}