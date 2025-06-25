<!-- <nav> -->
- [Akka](../index.html)
- [Operating](index.html)
- [Akka Automated Operations features](platform-features.html)

<!-- </nav> -->

# Akka Automated Operations features

Akka Automated Operations are based on a Kubernetes-based control plane and application plane for executing Akka services with fully-automated operations to enable elasticity, agility and resilience.

The fully-automated regions provide a broad set of functionality provided through Akka’s control plane and infrastructure automation:

## <a href="about:blank#_access_management"></a> Access management

| Users management, authorization | against organizations, projects, services |
| OpenID Connect support | delegation of user and group management |
| Management synchronization | synchronizing changes to users and authentication across every application plane |

## <a href="about:blank#_cloud_infrastructure_automation"></a> Cloud infrastructure automation

| Federated deployment | rolling out new application versions across many regions simultaneously |
| Multi-tenancy | multiple users and organizations sharing the same compute, storage, and persistence |
| Discovery | discovery of multiple Akka regions |
| Connectivity | mTLS setup within regions and for cross-region connectivity |
| Clustering | a service automatically spans its Akka cluster across multiple cloud provider availability zones |
| Kubernetes operators | mapping Akka apps to namespaces, roles and role bindings, mapping Akka services, secrets and routes to Kubernetes resources, admin management access to Akka apps deployed within Kubernetes including Views, projections |
| Auto-scaling | monitoring utilization to decide when additional instances of your Akka app should be added, removed, or resized from the cluster |
| Disaster recovery | data stores are backed up in different locations |

## <a href="about:blank#_development_structure"></a> Development structure

| Projects | create and manage projects that have shared users for collaborative development, deployment, and operations |
| Access tokens | access, refresh, and service tokens accessible to CI/CD and Akka developers |
| JWTs and ACLs | validate JSON Web Tokens (JWTs) for HTTP authentication, signing JWTs, allowing fine-grained invocation restrictions |
| Packing | coordinating the release of new application versions across registries in multiple regions |
| Akka Runtime | Managed JVM and Akka runtime that attach to Akka apps, can be updated and patched  independently of Akka apps without having to repack, redeploy, or restart the Akka service |

## <a href="about:blank#_networking"></a> Networking

| Firewall | VPC peering, ingress traffic routing. |
| Ingress | Let’s Encrypt TLS termination, client certificate CA validation, client certification authorization, Auto configuration of Akka routes for each service with TLS cert provisioning |

## <a href="about:blank#_observability"></a> Observability

| Aggregation | metrics, logs, traces are collected and aggregated to user-configured telemetry destinations whether the embedded metrics server or 3rd party service like Splunk or DataDog |
| Data management | credentials for the event store are dynamically generated for Akka services and injected into the runtime automatically; all data in motion, data at rest, and sourced keys are encrypted. Regular, automatic key rotation through Data Encryption Keys and Key Encryption Keys. |
| Heap dump collection | automatically capture JVM heap dumps in certain error scenarios |

<!-- <footer> -->
<!-- <nav> -->
[Enable CLI command completion](cli/command-completion.html) [Operator best practices](operator-best-practices.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->