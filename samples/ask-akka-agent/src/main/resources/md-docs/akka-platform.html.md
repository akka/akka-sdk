<!-- <nav> -->
- [Akka](../index.html)
- [Operating](index.html)
- [Akka Automated Operations](akka-platform.html)

<!-- </nav> -->

# Akka Automated Operations

Akka Agentic Platform provides capabilities to manage, monitor and gather insights from your application and its services once they are deployed. These resources will guide you through operating Akka applications and services on Akka Automated Operations. You should already be familiar with the [Deployment model](../concepts/deployment-model.html).

## <a href="about:blank#_full_automation_through_akka_automated_operations"></a> Full automation through Akka Automated Operations

Akka Automated Operations are based on a Kubernetes-based control plane and application plane for executing Akka services with fully-automated operations to enable elasticity, agility and resilience.

- [Akka Automated Operations features](platform-features.html)

## <a href="about:blank#_deploying_and_managing_services"></a> Deploying and Managing Services

Operating [Services](services/index.html) provides an overview of what services are and how to manage them.

- [Deploy and manage services](services/deploy-service.html)
- [Invoking Akka services](services/invoke-service.html)
- [Viewing data](services/view-data.html)
- [Data migration](services/data-management.html)
- [Integrating with CI/CD tools](integrating-cicd/index.html)

## <a href="about:blank#_observability_and_monitoring"></a> Observability and Monitoring

[Observability and monitoring](observability-and-monitoring/index.html) provides the tools and guidance you need to understand your running Akka services.

- [View logs](observability-and-monitoring/view-logs.html)
- [View metrics](observability-and-monitoring/metrics.html)
- [View traces](observability-and-monitoring/traces.html)
- [Exporting metrics, logs, and traces](observability-and-monitoring/observability-exports.html)

## <a href="about:blank#_organizations"></a> Organizations

[Organizations](organizations/index.html) are the root of the Akka management tree. All services and artifacts live inside of them. They are primarily a logical construct.

- [Managing organization users](organizations/manage-users.html)
- [Regions](organizations/regions.html)
- [Billing](organizations/billing.html)

## <a href="about:blank#_projects"></a> Projects

[Projects](projects/index.html) in Akka are the place where services are deployed to. They can span [Regions](organizations/regions.html) and are the central management point for operating groups of [Services](services/index.html) in Akka.

- [Create a new project](projects/create-project.html)
- [Managing project users](projects/manage-project-access.html)
- [Configure a container registry](projects/container-registries.html)

  - [Configure an external container registry](projects/external-container-registries.html)
- [Configure message brokers](projects/message-brokers.html)

  - [Aiven for Kafka](projects/broker-aiven.html)
  - [AWS MSK Kafka](projects/broker-aws-msk.html)
  - [Confluent Cloud](projects/broker-confluent.html)
  - [Google Pub/Sub](projects/broker-google-pubsub.html)

## <a href="about:blank#_regions"></a> Regions

Projects in Akka can span across [Regions](regions/index.html) with data automatically replicated between all the regions.

## <a href="about:blank#_cli"></a> CLI

Using the Akka CLI, you control all aspects of your Akka account from your command line. With it, you create and deploy new services, stream logs, and invite new developers to join your projects.

- [Install the Akka CLI](cli/installation.html)
- [Using the Akka CLI](cli/using-cli.html)
- [Enable CLI command completion](cli/command-completion.html)

<!-- <footer> -->
<!-- <nav> -->
[Self-managed operations](configuring.html) [Organizations](organizations/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->