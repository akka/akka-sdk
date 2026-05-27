<!-- <nav> -->
- [Akka](../index.html)
- [Operating](index.html)
- [Akka Automated Operations](akka-platform.html)

<!-- </nav> -->

# Akka Automated Operations

## <a href="about:blank#_overview"></a> Overview

Akka Automated Operations (AAO) is the operational backbone of the Akka platform. It manages, monitors, and gathers insights from your deployed Akka services so you can focus on building rather than babysitting infrastructure.

AAO is built on a Kubernetes-based control plane and application plane purpose-designed for executing Akka services with fully automated operations. It handles elasticity, agility, and resilience out of the box — you deploy your service and AAO takes care of the rest.

**When to use AAO:**

- You are moving an Akka service to production and need zero-touch deployments, scaling, and recovery.
- You require multi-region replication with automatic failover for business-critical workloads.
- You need deep observability — logs, metrics, and traces — integrated from day one.
- You operate under regulatory or data-sovereignty constraints that demand dedicated infrastructure.

## <a href="about:blank#_reliability"></a> Reliability

Reliability is a first-class dimension across every AAO tier. The platform is designed around the principle that failures are inevitable and recovery must be automatic:

- **99.9999% availability target** — six nines, measured across regions.
- **Sub-one-minute RTO** — your service resumes serving traffic in under 60 seconds after a region-level failure.
- **Zero-byte RPO** — event-sourced state is replicated across regions.
These guarantees are not aspirational — they are architectural. AAO continuously monitors region health, redistributes traffic on anomaly detection, and brings replacement capacity online without human intervention.

## <a href="about:blank#_deploying_and_managing_services"></a> Deploying and Managing Services

Operating [Services](services/index.html) provides an overview of what services are and how to manage them.

- [Deploy and manage services](services/deploy-service.html)
- [Invoking Akka services](services/invoke-service.html)
- [Viewing data](services/view-data.html)
- [Data migration](services/data-management.html)
- [Workload Identity](services/workload-identity.html)
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

## <a href="about:blank#_see_also"></a> See Also

- [Akka Automated Operations product page](https://akka.io/automated-operations)
- [Demo: Surviving the split — how Akka handles disaster scenarios](https://akka.io/blog/demo-surviving-the-split-how-akka-handles-disaster-scenarios)
- [Demo: Recovering a completely destroyed region](https://akka.io/blog/demo-recovering-a-completely-destroyed-region)

<!-- <footer> -->
<!-- <nav> -->
[Self-managed operations](configuring.html) [Organizations](organizations/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->