terraform {
  required_providers {
    rediscloud = {
      source = "RedisLabs/rediscloud"
      version = "2.0.0"
    }
  }
}

provider "google" {
    project = var.gcp_project
    region  = "us-east1"  
}

data "rediscloud_subscription" "autoscale-sub" {
    name = "Autoscaling Subscription"  
}

data "rediscloud_database" "autoscale-db" {
    subscription_id = data.rediscloud_subscription.autoscale-sub.id
    name = "autoscale-database"
}

data "google_compute_network" "autoscale_test_vpc" {
  name="autoscale-test-vpc"
}

data "google_compute_subnetwork" "autoscale_test_subnet" {
  name          = "autoscale-test-subnet"
  region        = "us-east1"
}

# Extract the hostname portion of the private_endpoint
locals {
  private_endpoint_host = join("", slice(split(":", data.rediscloud_database.autoscale-db.private_endpoint), 0, 1))
}

resource "google_compute_instance" "autoscaler-vm"{
    name = "autoscaler-vm"
    machine_type = "n1-standard-1"
    zone = "us-east1-b"
    boot_disk {
        initialize_params {
            image = "ubuntu-2004-focal-v20240731"
            size = 50
        }
    }

    network_interface {
        network = data.google_compute_network.autoscale_test_vpc.id
        subnetwork = data.google_compute_subnetwork.autoscale_test_subnet.id
        access_config {          
        }
    }
}

resource "null_resource" "build_app" {
    depends_on = [ google_compute_instance.autoscaler-vm ]
    provisioner "local-exec" {
        command = "./autoscaler/gradlew -p autoscaler clean bootjar"
    }

    connection {
        type = "ssh"
        host = google_compute_instance.autoscaler-vm.network_interface[0].access_config[0].nat_ip
        user = "steve_lorello_redis_com"
        private_key = file("~/.ssh/google_compute_engine")
    }

    provisioner "file" {
        source = "./autoscaler/build/libs/autoscaler-0.0.1-SNAPSHOT.jar"
        destination = "autoscaler.jar"      
    }

    ## install java 17
    provisioner "remote-exec" {
        inline = [
            #install java
            "sudo apt-get update",
            "sudo apt-get install -y openjdk-17-jdk",

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
            "echo 'Environment=REDIS_HOST_AND_PORT=${data.rediscloud_database.autoscale-db.private_endpoint}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_PASSWORD=${data.rediscloud_database.autoscale-db.password}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_CLOUD_API_KEY=${var.redis_cloud_api_key}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_CLOUD_ACCOUNT_KEY=${var.redis_cloud_account_key}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=REDIS_CLOUD_SUBSCRIPTION_ID=${data.rediscloud_subscription.autoscale-sub.id}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
            "echo 'Environment=ALERT_MANAGER_HOST=${google_compute_instance.autoscale-vm-prometheus.network_interface[0].access_config[0].nat_ip}' | sudo tee -a /etc/systemd/system/autoscaler.service > /dev/null",
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

resource "google_dns_record_set" "autoscaler_dns" {
    managed_zone = var.dns-zone-name
    name = "autoscaler.autoscale.${var.subdomain}."
    type = "A"
    ttl = 300
    rrdatas = [google_compute_instance.autoscaler-vm.network_interface[0].access_config[0].nat_ip]  
}


resource "google_compute_instance" "autoscale-vm-prometheus" {
    name = "autoscale-vm-prometheus"
    machine_type = "n1-standard-1"
    zone = "us-east1-b"
    boot_disk {
        initialize_params {
            image = "ubuntu-2004-focal-v20240731"
            size = 50
        }
    }

    network_interface {
        network = data.google_compute_network.autoscale_test_vpc.id
        subnetwork = data.google_compute_subnetwork.autoscale_test_subnet.id
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

      - job_name: 'rediscloud'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /
        scheme: https
        static_configs:
          - targets: ['${local.private_endpoint_host}:8070']
      - job_name: 'rediscloud-v2'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /v2
        scheme: https
        static_configs:
          - targets: ['${local.private_endpoint_host}:8070']
      - job_name: 'autoscaler-prometheus-actuator'
        scrape_interval: 30s
        scrape_timeout: 30s
        metrics_path: /actuator/prometheus
        scheme: http
        static_configs:
          - targets: ['${google_compute_instance.autoscaler-vm.network_interface[0].access_config[0].nat_ip}:8080']
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
          - url: 'http://${google_compute_instance.autoscaler-vm.name}:8080/alerts'
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

resource "google_dns_record_set" "autoscale_prometheus_dns" {
    managed_zone = var.dns-zone-name
    name = "prometheus.autoscale.${var.subdomain}."
    type = "A"
    ttl = 300
    rrdatas = [google_compute_instance.autoscale-vm-prometheus.network_interface[0].access_config[0].nat_ip]  
}
