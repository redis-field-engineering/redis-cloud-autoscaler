# Redis Cloud Autoscaler Quickstart

## Purpose

This is a quickstart guide for the Redis Cloud Autoscaler. It will show you how you can deploy the Redis Cloud Autoscaler alongside your Redis Cloud subscription.

## Configure Environment

Inside of the `gcp` folder you will find the `.tfvars.example` file. This file contains the environment variables that you need to set before you start the Redis Cloud Autoscaler. You can create a copy of this file called `.tfvars` and fill out the variables.

### Spinning up the Redis Cloud Autoscaler with terraform in GCP

To spin up the Redis Cloud Autoscaler with terraform in GCP, you can cd into the `gcp` folder and run the following command: `terraform init && terraform apply --var-file=.tfvars`. This will start the Redis Cloud Autoscaler in detached mode. and enter `yes` when prompted to apply the changes.

### Create some Rules

You can open the postman collection in the `postman` folder to create some rules for the Redis Cloud Autoscaler.

#### Configure Postman

1. In Postman import the `autoscaler.postman_collection.json` file in the `postman` folder.
2. In Postman import the `autoscaler.postman_environment.json` file in the `postman` folder.
3. Open the `autoscaler` environment.
4. Update the `base_url` to the base URL of the Redis Cloud Autoscaler.
7. Update the `db_id` to the ID of the database you want to scale.

#### Create a Rule

In Postman, open the Throughput rules or Memory rules collection.

1. Click on the `IncreaseThroughputDeterministic` or `IncreaseMemoryDeterministic` request.
2. Click on the `Body` tab.
3. Update the `scale_value` to the value you want to scale to (in ops/sec).
4. Click on the `Send` button.

You should see a 200 status code.
