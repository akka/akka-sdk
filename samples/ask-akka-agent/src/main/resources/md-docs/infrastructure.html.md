<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Regions](index.html)
- [Multi-region infrastructure](infrastructure.html)

<!-- </nav> -->

# Multi-region infrastructure

Akka multi-region is a series of features that allows Akka applications to replicate state between multiple Akka regions.
This page describes the infrastructure in place to enable these features. It does not explain the replication itself,
for a detailed understanding of how Akka replicates data across regions, the best place to go is the [Akka libraries documentation](https://doc.akka.io/libraries/akka-core/current/typed/replicated-eventsourcing.html) on replicated event sourcing.

The Akka infrastructure relies on three main features enabling multi-region replication:

1. Multi-region certificates
2. Multi-region ingress
3. Multi-region sync operator
Certificates are used for all communication between regions to authenticate. The multi-region ingress, combined
with certificates issued to each service, provides a means for applications running in different regions to communicate
with each other. The multi-region sync operator provides a means for deployed resources, such as services, routes, secrets,
and any other Akka resources deployed to Kubernetes, to be synchronized across regions, so that settings and configuration
automatically replicate to all connected regions.

## <a href="about:blank#_multi_region_constructs"></a> Multi Region Constructs

### <a href="about:blank#_region_groups"></a> Region groups

A *region group* is a group of regions that can participate in Akka’s multi-region features. For two services in different
regions to replicate data between each other, their regions need to be part of the same region group. Region groups are
isolated from each other, and cannot communicate or authenticate between each other. Region groups are typically used for
different environments, for example, there may be a production region group, and a development region group.

### <a href="about:blank#_mtls_authentication"></a> mTLS Authentication

All cross-region communication is authenticated using mutual Transport Layer Security (mTLS). Every client, and every
service, has a certificate issued to it, and unless both sides authenticate with each other using the client and server
certificate, the communication is blocked.

### <a href="about:blank#_root_cas"></a> Root CAs

Each region group has a separate root Certificate Authority (CA). The root CA is used to issue client and server certificates
for mTLS authentication. Since each region group uses a different Root CA, authenticated communication between region groups
is impossible and thus effectively isolated, since client certificates issued to services in one region group won’t be trusted
by services in any other region group.

### <a href="about:blank#_multi_region_certificates"></a> Multi-region certificates

All region group CAs are created on and managed by the Akka Federation Plane. It’s here that they are rotated. The private keys
for the root CAs are never shared anywhere outside the Akka Federation Plane.
For each region in a region group, an intermediate CA certificate is issued, managed and rotated by the Akka Federation Plane.
This certificate has name restrictions allowing the intermediate CA to only issue certificates for its local domain names. There
are two local domain names for a region, they are `*.${AKKA_REGION_APPS_HOSTNAME}` and `*.${AKKA_REGION_PLATFORM_HOSTNAME}`.
The Akka Federation Plane has a job that periodically syncs intermediate CA keys and certificates, along with the root CA
certificate, to all regions. The root CA certificate can be a list of CA certificates, this is to allow root CA rotation.

### <a href="about:blank#_multi_region_ingress"></a> Multi-region ingress

The multi-region ingress allows services to communicate with each other across regions. Each service is provisioned with its own
client certificate issued by that region’s intermediate CA, this is done by the deployment operator. The SAN of the client
certificate is in the format `<service-name>.<project-id>.svc.${AKKA_REGION_APPS_HOSTNAME}`.
The ingress is hosted at `ingress.svc.${AKKA_REGION_APPS_HOSTNAME}`. When authenticating, the ingress determines the service and
project name, and validates that the destination project matches the incoming project — thus preventing the ingress from being
used to cross project isolation boundaries. It then adds the client SAN to a header, so that the Akka SDK runtime can perform
further authorization as required.

## <a href="about:blank#_tls_server_certificates"></a> TLS Server Certificates

When exposing Akka services to the internet, Transport Layer Security (TLS) is used to secure all requests. There are three cases
for how TLS certificates are provisioned, depending on the type of hostname being used.

### <a href="about:blank#_auto_generated_hostname_certificates"></a> Auto-generated Hostname Certificates

By default, Akka provisions server certificates for auto-generated hostnames (e.g., `<generated-name>.${AKKA_REGION_APPS_HOSTNAME}`)
by solving DNS-01 challenges using [Let’s Encrypt](https://letsencrypt.org/). However, using Let’s Encrypt DNS-01 challenges isn’t
always feasible or possible, particularly for BYOC/BYOK8s customers who bring their own DNS zones that Akka does not control.
To support these cases, Akka allows customers to provide their own certificate provider configuration in the form of a cert-manager `ClusterIssuer`. When configured, the operator picks up the customer-provided `ClusterIssuer` and uses it in place of the default
issuer to automatically provision certificates for auto-generated hostnames.

|  | One caveat of this configuration is that the custom domain name cannot be the same as the apps domain name. |

### <a href="about:blank#_primary_project_hostnames"></a> Primary Project Hostnames

Customers can configure their own hostnames using the Akka CLI. In this case, TLS certificates are manually provisioned by the
customer. The customer creates a TLS secret using `akka secret create tls`, and then configures the ingress route to use this
certificate via `akka route update --server-certificate-secret`. This requires that the custom hostname is configured as a CNAME
record pointing to the region’s apps hostname.

For detailed instructions, see the custom server certificates documentation.

### <a href="about:blank#_secondary_apps_hostname_certificates"></a> Secondary Apps Hostname Certificates

For customers using a `secondary_apps_domain_name` for region or cluster-level failover routing, the same customer-provided `ClusterIssuer` used for auto-generated hostname certificates is reused to automatically provision certificates for the secondary
hostname.
This configuration enables DNS-level health checks and global traffic management patterns. The DNS structure follows a two-tier pattern: `*.${SECONDARY_APPS_HOSTNAME}` is configured as a CNAME record pointing to `${SECONDARY_APPS_HOSTNAME}`. The `${SECONDARY_APPS_HOSTNAME}` itself is configured as an A record pointing to the load balancer IP addresses. This separation is what enables failover routing with health
monitoring at the A record level, since CNAME records do not support health checks.

## <a href="about:blank#_specification_dns_and_firewall_setup"></a> Specification: DNS and Firewall Setup

### <a href="about:blank#_regional_dns_records_and_firewall_setup"></a> Regional DNS Records and Firewall Setup

| Hostname | Record Type | Target |
| --- | --- | --- |
| `akka.[customer_dns_zone]` | A | NLB |
| `*.akka.[customer_dns_zone]` | CNAME | `akka.[customer_dns_zone]` |
| `agents.akka.[customer_dns_zone]` | A | NLB |
| `*.agents.akka.[customer_dns_zone]` | CNAME | `agents.akka.[customer_dns_zone]` |

|  | Communication over port 443 should be allowed among all regions in the region group. |

### <a href="about:blank#_global_dns_records"></a> Global DNS Records

| Hostname | Record Type | Target |
| --- | --- | --- |
| `akka.[customer_global_dns_zone]` | A | IP Addresses |
| `*.akka.[customer_global_dns_zone]` | CNAME | `akka.[customer_global_dns_zone]` |

## <a href="about:blank#_references"></a> References

- [TLS certificates](../tls-certificates.html)
- [operations:tls-certificates.adoc#_custom_server_certificates](../tls-certificates.html#_custom_server_certificates)
- [Multi-region operations](../../concepts/multi-region.html)
- [Regions](index.html)

<!-- <footer> -->
<!-- <nav> -->
[Multi-region setup](setup.html) [TLS certificates](../tls-certificates.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->