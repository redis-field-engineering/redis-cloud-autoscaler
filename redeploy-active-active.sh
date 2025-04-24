#!/bin/bash

# Build the JAR
./gradlew clean bootjar

# Get the active-active VM instance name and zone from Terraform
cd active-active-terraform
AUTOSCALER_VM=aa-autoscaler-vm
ZONE=$(terraform output -raw aa_autoscaler_zone 2>/dev/null || echo "us-east1-b")  # Default to us-east1-b if not defined

echo "Deploying to Active-Active autoscaler VM at $AUTOSCALER_VM in zone $ZONE"

# Copy the JAR to the VM
gcloud compute scp ../autoscaler/redis-cloud-autoscaler/build/libs/redis-cloud-autoscaler-0.0.4.jar $AUTOSCALER_VM:~/autoscaler.jar --zone=$ZONE

# Deploy and restart the service
gcloud compute ssh --zone=$ZONE $AUTOSCALER_VM --command "sudo cp ~/autoscaler.jar /usr/local/bin/autoscaler.jar && sudo chown autoscaler:autoscaler /usr/local/bin/autoscaler.jar && sudo systemctl restart autoscaler"

echo "Deployment complete. Service restarted."