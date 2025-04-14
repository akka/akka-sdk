# Release notes for Akka

Akka constantly gets updates and improvements enabling new features and expanding on existing. This page lists all releases of Akka components including the Akka libraries.

Current versions

* [Akka SDK {akka-javasdk-version}](java:index.adoc)
* Akka CLI {akka-cli-version}
* A glance of all Akka libraries and their current versions is presented at [Akka library versions](https://doc.akka.io/libraries/akka-dependencies/current).

## January 2025

* [Akka Projections 1.6.8](https://github.com/akka/akka-projection/releases/tag/v1.6.8)
* [Akka Persistence R2DBC 1.3.2](https://github.com/akka/akka-persistence-r2dbc/releases/tag/v1.3.2)
* [Akka core 2.10.1](https://github.com/akka/akka/releases/tag/v2.10.1)
* [Akka SDK 3.1.0](https://github.com/akka/akka-sdk/releases/tag/v3.1.0)
  * Internal refactoring of SPI between SDK and runtime
  * Akka runtime 1.3.0
* Akka CLI 3.0.9
  * Fixes listing of user role bindings
* Platform update 2025-01-13
  * updates to internal libraries for security fixes
  * switch of internal framework to apply environment configuration
  * minor updates to the Console

## December 2024

* Akka CLI 3.0.8
  * Updates to configure SSO integrations
* [Akka SDK 3.0.2](https://github.com/akka/akka-sdk/releases/tag/v3.0.2)
  * Integration Tests are now bound to `mvn verify` and not a specific profile
* Platform update 2024-12-10
  * New internal structure to capture usage data
  * Updated email server for signup emails
  * Updated JVM memory settings for services
  * Akka Runtime 1.2.5
  * Better gRPC support for the CLI
  * Console updates
    * Empty projects can now be deleted from the Console
  * GCP: Updates of GKE node versions
* Akka Runtime 1.2.5
  * Improves handling of `count(*)` in the view query language
* Akka CLI 3.0.7
  * Improvements to the Local Console
* [Akka SDK 3.0.1](https://github.com/akka/akka-sdk/releases/tag/v3.0.1)
  * Minor improvements

## November 2024

* [Akka Projections 1.6.5](https://github.com/akka/akka-projection/releases/tag/v1.6.5)
* [Akka Projections 1.6.4](https://github.com/akka/akka-projection/releases/tag/v1.6.4)
* [Akka Projections 1.6.3](https://github.com/akka/akka-projection/releases/tag/v1.6.3)
* [Akka DynamoDB 2.0.3](https://github.com/akka/akka-persistence-dynamodb/releases/tag/v2.0.3)
* [Akka DynamoDB 2.0.2](https://github.com/akka/akka-persistence-dynamodb/releases/tag/v2.0.2)
* Akka CLI 3.0.6
  * Automatically retry calls
  * Improved help texts
* [Akka Projections 1.6.2](https://github.com/akka/akka-projection/releases/tag/v1.6.2)
* [Akka DynamoDB 2.0.1](https://github.com/akka/akka-persistence-dynamodb/releases/tag/v2.0.1)
* Akka Runtime 1.2.2
  * Disable projection scaling until issue has been investigated and fixed
  * fix problem with read only commands in workflows
* Akka SDK 3.0.0
  * Runtime 1.2.1
  * Accept old type url for components that can consume pre-existing events
* Akka Runtime 1.2.1
  * Remove logback startup warnings
  * Don’t log TImeoutException at error level
  * Allow root route for both sdks
* Akka CLI 3.0.4
  * Changed Docker credentials commands
  * Improved logging commands
  * New commands for dynamic logging levels (`akka service logging`)
* Akka SDK 3.0.0-RC4
  * Fix dependency excludes
* Akka SDK 3.0.0-RC1
  * Json type url cleanup
  * Allow more customization of brokers in dev mode
  * Akka dependencies
  * Smaller improvements
* Akka Runtime 1.2.0
  * Fix configuration for tracing
  * Json type url cleanup
  * Allow more customization of brokers in dev mode
  * Akka dependencies
  * Smaller improvements
* [Akka Projections 1.6.1](https://github.com/akka/akka-projection/releases/tag/v1.6.1)
  * Configurable parallelism in initial offset store query for AWS DynamoDB
* Akka Runtime 1.1.53
  * Several smaller bug fixes and improvements
* Akka Runtime 1.1.52
  * Several smaller bug fixes and improvements

## October 2024

* Akka Runtime 1.1.51
  * Several smaller bug fixes and improvements
* Akka CLI 3.0.3
  * Improved support for pushing Service images to multiple Akka Container Registries
* Akka libraries 24.10 releases
  * overview in [release-notes/2024-10-30-akka-24.10-released.adoc](release-notes/2024-10-30-akka-24.10-released.adoc)
* Akka Runtime 1.1.50
  * Several smaller bug fixes and improvements
* Akka Runtime 1.1.49
  * JWT support for HTTP Endpoints
  * Several smaller bug fixes and improvements
* Akka CLI 3.0.2
  * Added region synchronisation status for the following commands:
    * akka service get
    * akka service list
    * akka routes get
    * akka routes list
    * akka project observability get
  * Region management
  * Data export and import management
* Akka Runtime 1.1.46
  * View indexing improvements for some join conditions
  * Other smaller improvements
* [Akka Projection 1.5.9](https://github.com/akka/akka-projection/releases/tag/v1.5.9)
  * improvement of projection scaling
* [Akka Persistence R2DBC 1.2.6](https://github.com/akka/akka-persistence-r2dbc/releases/tag/v1.2.6)
  * improvement of latency for eventsBySlices after idle
* [Akka Projection 1.5.8](https://github.com/akka/akka-projection/releases/tag/v1.5.8)
  * fix protobuf serialization in Replicated Event Sourcing
* [Akka core 2.9.7](https://github.com/akka/akka/releases/tag/v2.9.7)
  * event interceptor in Replicated Event Sourcing
  * expose license key expiry
* [Akka Projection 1.5.7](https://github.com/akka/akka-projection/releases/tag/v1.5.7)
  * dependency updates
* [Akka gRPC 2.4.4](https://github.com/akka/akka-grpc/releases/tag/v2.4.4)
  * Allow rotation of client certs
  * updates for [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8)
* [Akka core 2.9.6](https://github.com/akka/akka/releases/tag/v2.9.6)
  * updates for [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8)
  * [release-notes/2024-10-02-akka-2.9.6-released.adoc](release-notes/2024-10-02-akka-2.9.6-released.adoc)
  * license key validation
* [Akka core 2.8.7](https://github.com/akka/akka/releases/tag/v2.8.7)
  * [release-notes/2024-10-02-akka-2.9.6-released.adoc](release-notes/2024-10-02-akka-2.9.6-released.adoc)
* [Akka core 2.7.1](https://github.com/akka/akka/releases/tag/v2.7.1)
  * [release-notes/2024-10-02-akka-2.9.6-released.adoc](release-notes/2024-10-02-akka-2.9.6-released.adoc)

## May 2024

* Akka libraries 24.05 releases
  * overview in [release-notes/2024-05-17-akka-24.05-released.adoc](release-notes/2024-05-17-akka-24.05-released.adoc)

## October 2023

* Akka libraries 23.10 releases
  * overview in [release-notes/2023-10-31-akka-23.10-released.adoc](release-notes/2023-10-31-akka-23.10-released.adoc)

## May 2023

* Akka libraries 23.05 releases
  * overview in [release-notes/2023-05-16-akka-23.5-released.adoc](release-notes/2023-05-16-akka-23.5-released.adoc)

## October 2022

* Akka libraries 22.10 releases
  * overview in [release-notes/2022-10-26-akka-22.10-released.adoc](release-notes/2022-10-26-akka-22.10-released.adoc)
