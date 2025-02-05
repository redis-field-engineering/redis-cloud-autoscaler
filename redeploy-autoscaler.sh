#!/bin/bash

./autoscaler/gradlew -p autoscaler clean bootjar
gcloud compute scp autoscaler/build/libs/autoscaler-0.0.1-SNAPSHOT.jar autoscaler-vm:~/autoscaler.jar --zone=us-east1-b
# run command to move the jar to /usr/local/bin/autoscaler.jar, change owner to autoscaler, and restart the autoscaler service
gcloud compute ssh --zone=us-east1-b autoscaler-vm --command "sudo cp ~/autoscaler.jar /usr/local/bin/autoscaler.jar && sudo chown autoscaler:autoscaler /usr/local/bin/autoscaler.jar && sudo systemctl restart autoscaler"