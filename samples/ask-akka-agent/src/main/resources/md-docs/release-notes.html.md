<!-- <nav> -->
- [Akka](../index.html)
- [Reference](index.html)
- [Release notes](release-notes.html)

<!-- </nav> -->

# Release notes for Akka

Akka constantly gets updates and improvements enabling new features and expanding on existing. This page lists all releases of Akka components including the Akka libraries.

Current versions

- [Akka SDK 3.5.15](../sdk/index.html)
- Akka CLI 3.0.47
- A glance of all Akka libraries and their current versions is presented at [Akka library versions](https://doc.akka.io/libraries/akka-dependencies/current).

## <a href="about:blank#_march_2026"></a> March 2026

- [Akka SDK 3.5.15](https://github.com/akka/akka-sdk/releases/tag/v3.5.15)

  - PDF documents support in Agent user message
  - Token usage tracking in session memory
  - Agent reply wrapper in component client
  - Protobuf serialization support
  - Custom model request headers

## <a href="about:blank#_february_2026"></a> February 2026

**Views and consumers can start from snapshots**

Views and consumers can now be configured to [start from a snapshot](../sdk/consuming-producing.html#_starting_from_snapshot) instead of replaying the full event history. This significantly reduces startup time and resource usage for components that subscribe to high-volume event streams.

- Akka CLI 3.0.48

  - Improvements to `akka project apply` command
  - Updated data management commands
  - Allow `akka project get` without project name to retrieve the current configured project
- [Akka SDK 3.5.14](https://github.com/akka/akka-sdk/releases/tag/v3.5.14)

  - maven multi-module support improvements
  - start views and consumers from snapshot
  - Testkit improvements
- Akka CLI 3.0.47

  - Improvements to the 'akka code update-context' command

## <a href="about:blank#_january_2026"></a> January 2026

**Smarter agents with vision and reasoning**

Agents gain [multimodal capabilities with image input support](../sdk/agents/prompt.html#_multimodal_user_message), enabling models to perceive and reason about visual content. Supported models can now be configured to enable thinking, letting agents reason through complex problems step by step. Other Akka components can be wired directly as agent tools, making it easy to give agents access to your existing business logic without writing glue code.

**Workflow notifications and a new fluent API**

Workflows get [a redesigned, more type-safe and fluent API](../sdk/workflows.html) that makes defining multi-step processes more intuitive. Workflows can now emit streams of real-time notifications to subscribers, keeping clients and downstream systems informed as individual workflows progress through their steps.

**Console with deep observability**

The Akka console now provides detailed insights into your service structure and component interactions. [Run it in local mode](../sdk/running-locally.html#_local_console) to get full request traces showing exactly which components handle each request, along with a detailed view of how agents interact with AI models — invaluable for debugging and understanding agent behavior during development.

- [Akka SDK 3.5.13](https://github.com/akka/akka-sdk/releases/tag/v3.5.13)

  - Workflow notifications
  - WebSocket support
  - Pluggable agent image loading
  - Agent thinking
- Akka CLI 3.0.46

  - Observability support improved
  - External secrets
  - Support for controlling CLI configuration with MDM solutions
- Akka CLI 3.0.45

  - Support for configuring custom JVM Truststore
  - Command for bringing up region
- [Akka SDK 3.5.12](https://github.com/akka/akka-sdk/releases/tag/v3.5.12)

  - agent image input mime type support
  - replication filters for Event Sourced Entity and Key Value Entity
  - AI assistance instruction improvements

## <a href="about:blank#_december_2025"></a> December 2025

- Akka CLI 3.0.44

  - Support for configuring Azure observability
  - Project level descriptors through `akka project export`, `akka project apply`
  - `akka code check`, check if project is ready for local development
  - option to wrap log lines in `akka local cluster`
  - fixed issue when shutting down a local cluster
- [Akka SDK 3.5.11](https://github.com/akka/akka-sdk/releases/tag/v3.5.11)

  - resumable SSE for streaming updates view queries
  - workflow global timeout and pause timeout configuration
- [Akka SDK 3.5.10](https://github.com/akka/akka-sdk/releases/tag/v3.5.10)

  - image input support in Agents
  - Streaming HTTP endpoint improvements
- Akka Runtime 1.5.22 - 1.5.27

  - many improvements around Consumers/Views subscriptions

## <a href="about:blank#_november_2025"></a> November 2025

- Akka CLI 3.0.40

  - New: `akka organization auth test` command
  - Better support for Podman
  - Bug fixes
- [Akka SDK 3.5.9](https://github.com/akka/akka-sdk/releases/tag/v3.5.9)

  - fix compile time validation for private methods
- [Akka SDK 3.5.8](https://github.com/akka/akka-sdk/releases/tag/v3.5.8)

  - fix compile time validation for Agent handlers
- [Akka SDK 3.5.7](https://github.com/akka/akka-sdk/releases/tag/v3.5.7)

  - component as agent function tool
  - agent memory filters
  - workflow passivation settings
  - compile time validations
  - direct consumption of protobuf messages in consumer
  - include basic protos and document external protos
  - updated AI coding assistant docs
  - Runtime 1.5.22
- Akka Runtime 1.5.20 - 1.5.22

  - fix gRPC virtual thread regression
  - sampling priority for debug tracing
  - Akka libraries 25.10 dependencies
  - Recreate state for entity with mutable state
  - filter out messages from unknown tools
  - improvements of metrics and tracing
- Akka libraries 25.10 releases

  - overview in [Akka 25.10 Released](release-notes/2025-11-05-akka-25.10-released.html)
- Akka CLI release 3.0.32 release

  - `akka local cluster` - New command for launching a local cluster from a project
  - `akka local console` - Multiple UI updates
  - `akka code token` - Provision an Akka download token
  - `akka code init` - Initiated a new Akka SDK project

## <a href="about:blank#_october_2025"></a> October 2025

- [Akka SDK 3.5.6](https://github.com/akka/akka-sdk/releases/tag/v3.5.6)

  - BigDecimal support in endpoints and views
  - Santization/anonymization in logs and agents
- Akka Runtime 1.5.19

  - Agent interaction log
- [Akka SDK 3.5.5](https://github.com/akka/akka-sdk/releases/tag/v3.5.5)

  - Include `EvaluatorResult.passed` in json response
  - Use `@Component` and new `@AgentRole` annotations instead of `@AgentDescriptor`
  - Comprehensive CLAUDE.md
- [Akka SDK 3.5.4](https://github.com/akka/akka-sdk/releases/tag/v3.5.4)

  - Agent guardrails
  - Evaluator agents
  - New `@Component` annotation, replacing `@ComponentId`
  - Better error handling in JsonSchema generation
  - Runtime 1.5.18
- Akka Runtime 1.5.18

  - Turn unknown tool into ToolCallExecutionFailure
  - Telemetry for evaluation results
- Akka Runtime 1.5.17

  - Telemetry for view update sequence gap detection
  - Agent guardrails

## <a href="about:blank#_september_2025"></a> September 2025

- [Akka SDK 3.5.3](https://github.com/akka/akka-sdk/releases/tag/v3.5.3)

  - User configurable timeouts and retries for model and MCP
  - Environment variable configuration for bearer token issuer
  - Enhanced telemetry context for MCP endpoints and agent commands
  - Local cluster formation for testing failure scenarios
  - Hello world agent Kotlin sample
- [Akka SDK 3.5.2](https://github.com/akka/akka-sdk/releases/tag/v3.5.2)

  - Persist metadata together with event
  - Include json schema for the structured response
  - Documentation improvements
  - Various bug fixes and internal improvements
- Akka Runtime 1.5.16

  - Langchain4j 1.5.0
- Akka Runtime 1.5.15

  - Fix unexpected rejected events for views and consumers
  - Allow configuration of model and MCP timeouts and retries
  - Improvements to tracing
  - Support local cluster
- Akka Runtime 1.5.14

  - Enriched tracing

## <a href="about:blank#_august_2025"></a> August 2025

- [Akka SDK 3.5.1](https://github.com/akka/akka-sdk/releases/tag/v3.5.1)

  - GPT-5 support
  - Various bug fixes and internal improvements
- [Akka SDK 3.5.0](https://github.com/akka/akka-sdk/releases/tag/v3.5.0)

  - New typesafe API for workflows - previous Step API is deprecated
  - Improved error handling and serializable UserException
  - Improved HTTP endpoint path consistency
  - Full telemetry context propagation
  - Documentation updates: new and improved workflow samples, background execution, async invocation, and service description docs
  - Various bug fixes and internal improvements

## <a href="about:blank#_july_2025"></a> July 2025

- [Akka SDK 3.4.1](https://github.com/akka/akka-sdk/releases/tag/v3.4.1)

  - Improved error handling for AI models and tool exceptions
  - Enhanced JsonSchema creation by supporting recursive types and additional standard library types
  - Self service resolution in integration tests
- [Akka core 2.10.7](https://github.com/akka/akka/releases/tag/v2.10.7)

## <a href="about:blank#_june_2025"></a> June 2025

**Agent component**

AI agents integrate with AI to perceive their environment, make decisions, and take actions toward a specific goal. Agents can have varying degrees of human intervention from none (completely autonomous) to requiring a human to approve each action the agent takes.

The new [Agent](../sdk/agents.html) component is typically backed by a large language model (LLM). It maintains contextual history in a session memory, which may be shared between multiple agents that are collaborating on the same goal. It may provide function tools and call them as requested by the model.

Several agents can collaborate to achieve a common goal, and they can be orchestrated from a predefined workflow or a dynamically created plan.

**Akka’s local console more lightweight**

The latest Akka CLI versions run the local console for development without using Docker. It spins up quickly and requires almost no resources.

**New Akka SDK learning path in Akkademy**

A new course is now available in Akkademy: [Introduction to the Akka SDK](https://akkademy.akka.io/learn/courses/51/introduction-to-the-akka-sdk).

This short, self-paced course introduces the foundational concepts of the Akka SDK. It is organized into brief lessons with coding exercises that run directly in the browser, requiring no installation. The course is ideal for developers who are new to the Akka SDK and want a structured, guided approach to building services.

- Akka CLI 3.0.23

  - The new `akka code init` command supports kicking off an Akka project easily.
- [Akka SDK 3.4.0](https://github.com/akka/akka-sdk/releases/tag/v3.4.0)

  - Agent component
  - Agent session memory
  - Agent function tools
  - Agent prompt template
  - MCP server and client support
  - New getting started guides
  - Many samples with AI agents
  - Agent and workflow tracing
  - Documentation updates
- Akka CLI 3.0.22

  - Akka’s local console is now built-in — runs quickly and doesn’t require Docker anymore!
  - New `akka projects settings` command that supports removing an unavailable region from multi-region projects
- Akka Runtime 1.4.14

  - Internal improvements
- Akka Runtime 1.2.19

  - Internal improvements

## <a href="about:blank#_may_2025"></a> May 2025

- [Akka SDK 3.3.2](https://github.com/akka/akka-sdk/releases/tag/v3.3.2)

  - [AI coding assistant instructions](../sdk/ai-coding-assistant.html)
  - Support deletion of workflows
  - Validation for missing handler when consuming from Key Value Entity or Workflow
- Akka Runtime 1.4.12

  - Akka 25.05.0 dependencies
  - Jackson 2.18.3
- Akka Runtime 1.4.11

  - Improved metrics
  - Basis for SDK additions
- [Akka SDK 3.3.1](https://github.com/akka/akka-sdk/releases/tag/v3.3.1)

  - Support `Runnable` in Workflow calls
  - Updates to [Operate Akka in a self-managed environment](../operations/configuring.html) documentation
  - Publish LLM friendly markdown docs
- Akka libraries 25.05 releases

  - overview in [Akka 25.05 Released](release-notes/2025-05-06-akka-25.05-released.html)
- Akka CLI 3.0.18

  - Improvements and upgrades

## <a href="about:blank#_april_2025"></a> April 2025

**Self managed deployment**

For organizations that want control over how Akka services are installed, updated, and maintained. Akka services are packaged into standalone binaries with Akka clustering for scaling and deploy to self-managed nodes. You are responsible for separately managing secure connectivity, routes, installation, deployment, and persistence.

This new deployment alternative is described in [Operate Akka in a self-managed environment](../operations/configuring.html).

**Virtual threads**

SDK running on virtual threads means it is possible to benefit from the performance of the underlying async Akka runtime, without having to use complex completion stage composition. The service code can now look like regular, simple, Java code.

The [shopping cart endpoint](../getting-started/shopping-cart/build-and-deploy-shopping-cart.html#_the_external_api) is one example of how that looks like

- [Akka Management 1.6.1](https://github.com/akka/akka-management/releases/tag/v1.6.1)

  - [CVE-2025-46548](security-announcements/akka-management-cve-2025-46548.html)
- Akka CLI 3.0.17

  - Improvements to the `akka service proxy` command when using `--use-grpc-web`
- Akka CLI 3.0.16

  - Manage multi-region aka global hostnames
  - Introduce `akka projects hostnames update`
  - Configure OpenTelemetry HTTP collectors (additional to gRPC)
- Akka SDK 3.3.0

  - User logic now runs on virtual threads. This includes some breaking API changes, see [migration guide](migration-guide.html) for details.
  - New APIs for retries
- Akka SDK 3.2.4

  - Support for [self-managed nodes](../operations/configuring.html) in your infrastructure, or any cloud service.
- Akka Runtime 1.4.5, 1.4.6, 1.4.7, 1.4.8

  - Views support for self-joins
  - Improved metrics for consumers and views
  - Workflow improvements
- Akka Runtime 1.2.14 1.2.15

  - Views support for self-joins
  - Improved metrics for consumers and views
  - Workflow improvements
- [Akka Projections 1.6.11](https://github.com/akka/akka-projection/releases/tag/v1.6.10)
- [Akka core 2.10.3](https://github.com/akka/akka/releases/tag/v2.10.3)

  - Support for virtual threads on Java 21
  - New API for retries

## <a href="about:blank#_march_2025"></a> March 2025

**Replication of Key Value Entities**

Akka now supports multi-region replication of [Key Value Entities](../sdk/key-value-entities.html). No code changes are needed for activating replication. This feature is disabled until the next full restart of your service (`akka service pause/resume`). It’s recommended to add the `ReadOnlyEffect` as described in [Key Value Entity replication](../sdk/key-value-entities.html#_replication).

**Service-to-service eventing for Key Value Entities**

[Service to service eventing](../sdk/consuming-producing.html#s2s-eventing) is now supported for [Key Value Entities](../sdk/key-value-entities.html). Key value changes are captured as events which can propagate state changes and information between Akka services within the same project.

**New primary selection mode**

[Request-region primary selection mode](../concepts/multi-region.html): The primary region changes when another region receives a write request. Upon a write request to an entity in a region that is not the primary it will move its primary. The new primary ensures that all preceding events from the previous primary have been fully replicated and applied (i.e. persisted) before writing the new event, and thereby guarantees strong consistency when switching from one region to another. Subsequent write requests to the primary region of the entity are handled locally without any further coordination. Write requests to other regions will trigger the same switch-over process. All other entity instances operate unimpeded during the switch-over process.

**Automatic deduplication for view updates**

When subscribing to entities and service-to-service eventing from a view, updates are now guaranteed to be processed exactly-once. This is not applicable when subscribing to topics. Additionally, a new section describing different [message deduplication](../sdk/dev-best-practices.html#message-deduplication) strategies has been added to the best practices guide.

- Akka CLI 3.0.15

  - improve Docker helper installation on Windows
- [Akka SDK 3.2.3](https://github.com/akka/akka-sdk/releases/tag/v3.2.3)

  - Workflow subscriptions
- [Akka SDK 3.2.2](https://github.com/akka/akka-sdk/releases/tag/v3.2.2)

  - make it possible to define custom Jackson object mapper for endpoints
  - query parameters support for HTTP Endpoints
  - documenting deduplication strategies for common use cases
- Akka Runtime 1.4.3, 1.4.4

  - automatic deduplication of views updates
  - improvements for use of reserved words in view queries
  - support for endpoint custom object mapper
  - support for query parameters in HTTP requests
  - increased maximum size for service to service eventing
  - validate event size to ensure replication can always be enabled
  - updated runtime base docker image
  - support for workflow subscriptions in eventing
- Akka Runtime 1.2.11, 1.2.12, 1.12.13

  - automatic deduplication of views updates
  - improvements for use of reserved words in view queries
  - increased maximum size for service to service eventing
  - updated runtime base docker image
- [Akka SDK 3.2.1](https://github.com/akka/akka-sdk/releases/tag/v3.2.1)

  - make it possible to filter message by region origin, see [Consumer documentation](../sdk/consuming-producing.html#_multi_region_replication)
  - checking serialization in entity testkits
  - shutdown immediately if runtime fails at startup
  - Akka Chess sample
  - Akka Runtime 1.4.2
- Akka Runtime 1.4.0, 1.4.1, 1.4.2

  - Key Value Entity Replication
  - new primary selection mode
  - bind local dev to 127.0.0.1 interface
  - improve view support for parameters and constants
  - validate view parameter array typing for = ANY
  - expose self-region and origin-region
  - rename primary selection modes
- Akka Runtime 1.2.10

  - improve view support for parameters and constants
  - validate view parameter array typing for = ANY
- Akka CLI 3.0.14

  - Bug fix for docker repository
- [Akka Projections 1.6.10](https://github.com/akka/akka-projection/releases/tag/v1.6.10)

## <a href="about:blank#_february_2025"></a> February 2025

**gRPC endpoints**

Akka now supports [gRPC endpoints](../sdk/grpc-endpoints.html) complementing [HTTP endpoints](../sdk/http-endpoints.html). gRPC services enable contract-first development where interfaces can be defined with the protobuf specification. The underlying protobuf serialization format supports backward and forward compatibility avoiding tight coupling by making it easier to evolve services without breaking existing clients. Additionally, gRPC’s supports unary and streaming calls making it a good choice for high-performance service-to-service communication.

**Serving static resources from HTTP endpoints**

HTTP endpoints can now serve static content. This enables hosting web pages, client-side applications, and other resources within the endpoint. The static content is accessible through a convenience method: [Serving static content](../sdk/http-endpoints.html#_serving_static_content). [Subtree "glob" paths](../sdk/http-endpoints.html#glob_static_content) allow serving path hierarchies.

**Server-sent events**

Server-sent events (SSE) is a way to push a stream of elements through a single HTTP response enabling an HTTP client to see each element one-by-one rather than waiting for an entire response to complete. This is useful for streaming events from a View or monitoring state changes of an entity. We have introduced an API to simplify deferred HTTP responses as a stream of [Server-sent Events (SSE)](../sdk/http-endpoints.html#sse).

- [Akka SDK 3.2.0](https://github.com/akka/akka-sdk/releases/tag/v3.2.0)
- Akka Runtime 1.3.6

  - View query parameter improvements
  - Wildcard path support
- Akka Runtime 1.2.9

  - View query parameter improvements
- [Akka core 2.10.2](https://github.com/akka/akka/releases/tag/v2.10.2)
- [Akka Projections 1.6.9](https://github.com/akka/akka-projection/releases/tag/v1.6.9)
- Akka CLI 3.0.12

  - Fixes to TLS certificate creation
  - Improved error reporting
- [Akka Persistence R2DBC 1.3.3](https://github.com/akka/akka-persistence-r2dbc/releases/tag/v1.3.3)

## <a href="about:blank#_january_2025"></a> January 2025

- [Akka Projections 1.6.8](https://github.com/akka/akka-projection/releases/tag/v1.6.8)
- [Akka Persistence R2DBC 1.3.2](https://github.com/akka/akka-persistence-r2dbc/releases/tag/v1.3.2)
- [Akka Persistence DynamoDB 2.0.5](https://github.com/akka/akka-persistence-dynamodb/releases/tag/v2.0.5)
- [Akka core 2.10.1](https://github.com/akka/akka/releases/tag/v2.10.1)
- [Akka SDK 3.1.0](https://github.com/akka/akka-sdk/releases/tag/v3.1.0)

  - Internal refactoring of SPI between SDK and runtime
  - Akka runtime 1.3.0
- Akka CLI 3.0.9

  - Fixes listing of user role bindings
- Platform update 2025-01-13

  - updates to internal libraries for security fixes
  - switch of internal framework to apply environment configuration
  - minor updates to the Console

## <a href="about:blank#_december_2024"></a> December 2024

- Akka CLI 3.0.8

  - Updates to configure SSO integrations
- [Akka SDK 3.0.2](https://github.com/akka/akka-sdk/releases/tag/v3.0.2)

  - Integration Tests are now bound to `mvn verify` and not a specific profile
- Platform update 2024-12-10

  - New internal structure to capture usage data
  - Updated email server for signup emails
  - Updated JVM memory settings for services
  - Akka Runtime 1.2.5
  - Better gRPC support for the CLI
  - Console updates

    - Empty projects can now be deleted from the Console
  - GCP: Updates of GKE node versions
- Akka Runtime 1.2.5

  - Improves handling of `count(*)` in the view query language
- Akka CLI 3.0.7

  - Improvements to the Local Console
- [Akka SDK 3.0.1](https://github.com/akka/akka-sdk/releases/tag/v3.0.1)

  - Minor improvements

## <a href="about:blank#_november_2024"></a> November 2024

- [Akka Projections 1.6.5](https://github.com/akka/akka-projection/releases/tag/v1.6.5)
- [Akka Projections 1.6.4](https://github.com/akka/akka-projection/releases/tag/v1.6.4)
- [Akka Projections 1.6.3](https://github.com/akka/akka-projection/releases/tag/v1.6.3)
- [Akka DynamoDB 2.0.3](https://github.com/akka/akka-persistence-dynamodb/releases/tag/v2.0.3)
- [Akka DynamoDB 2.0.2](https://github.com/akka/akka-persistence-dynamodb/releases/tag/v2.0.2)
- Akka CLI 3.0.6

  - Automatically retry calls
  - Improved help texts
- [Akka Projections 1.6.2](https://github.com/akka/akka-projection/releases/tag/v1.6.2)
- [Akka DynamoDB 2.0.1](https://github.com/akka/akka-persistence-dynamodb/releases/tag/v2.0.1)
- Akka Runtime 1.2.2

  - Disable projection scaling until issue has been investigated and fixed
  - fix problem with read only commands in workflows
- Akka SDK 3.0.0

  - Runtime 1.2.1
  - Accept old type url for components that can consume pre-existing events
- Akka Runtime 1.2.1

  - Remove logback startup warnings
  - Don’t log TImeoutException at error level
  - Allow root route for both sdks
- Akka CLI 3.0.4

  - Changed Docker credentials commands
  - Improved logging commands
  - New commands for dynamic logging levels (`akka service logging`)
- Akka SDK 3.0.0-RC4

  - Fix dependency excludes
- Akka SDK 3.0.0-RC1

  - Json type url cleanup
  - Allow more customization of brokers in dev mode
  - Akka dependencies
  - Smaller improvements
- Akka Runtime 1.2.0

  - Fix configuration for tracing
  - Json type url cleanup
  - Allow more customization of brokers in dev mode
  - Akka dependencies
  - Smaller improvements
- [Akka Projections 1.6.1](https://github.com/akka/akka-projection/releases/tag/v1.6.1)

  - Configurable parallelism in initial offset store query for AWS DynamoDB
- Akka Runtime 1.1.53

  - Several smaller bug fixes and improvements
- Akka Runtime 1.1.52

  - Several smaller bug fixes and improvements

## <a href="about:blank#_october_2024"></a> October 2024

- Akka Runtime 1.1.51

  - Several smaller bug fixes and improvements
- Akka CLI 3.0.3

  - Improved support for pushing Service images to multiple Akka Container Registries
- Akka libraries 24.10 releases

  - overview in [Akka 24.10 Released](release-notes/2024-10-30-akka-24.10-released.html)
- Akka Runtime 1.1.50

  - Several smaller bug fixes and improvements
- Akka Runtime 1.1.49

  - JWT support for HTTP Endpoints
  - Several smaller bug fixes and improvements
- Akka CLI 3.0.2

  - Added region synchronisation status for the following commands:

    - akka service get
    - akka service list
    - akka routes get
    - akka routes list
    - akka project observability get
  - Region management
  - Data export and import management
- Akka Runtime 1.1.46

  - View indexing improvements for some join conditions
  - Other smaller improvements
- [Akka Projection 1.5.9](https://github.com/akka/akka-projection/releases/tag/v1.5.9)

  - improvement of projection scaling
- [Akka Persistence R2DBC 1.2.6](https://github.com/akka/akka-persistence-r2dbc/releases/tag/v1.2.6)

  - improvement of latency for eventsBySlices after idle
- [Akka Projection 1.5.8](https://github.com/akka/akka-projection/releases/tag/v1.5.8)

  - fix protobuf serialization in Replicated Event Sourcing
- [Akka core 2.9.7](https://github.com/akka/akka/releases/tag/v2.9.7)

  - event interceptor in Replicated Event Sourcing
  - expose license key expiry
- [Akka Projection 1.5.7](https://github.com/akka/akka-projection/releases/tag/v1.5.7)

  - dependency updates
- [Akka gRPC 2.4.4](https://github.com/akka/akka-grpc/releases/tag/v2.4.4)

  - Allow rotation of client certs
  - updates for [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8)
- [Akka core 2.9.6](https://github.com/akka/akka/releases/tag/v2.9.6)

  - updates for [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8)
  - [Akka license key](release-notes/2024-10-02-akka-2.9.6-released.html)
  - license key validation
- [Akka core 2.8.7](https://github.com/akka/akka/releases/tag/v2.8.7)

  - [Akka license key](release-notes/2024-10-02-akka-2.9.6-released.html)
- [Akka core 2.7.1](https://github.com/akka/akka/releases/tag/v2.7.1)

  - [Akka license key](release-notes/2024-10-02-akka-2.9.6-released.html)

## <a href="about:blank#_may_2024"></a> May 2024

- Akka libraries 24.05 releases

  - overview in [Akka 24.05 released](release-notes/2024-05-17-akka-24.05-released.html)

## <a href="about:blank#_october_2023"></a> October 2023

- Akka libraries 23.10 releases

  - overview in [Akka 23.10 Released](release-notes/2023-10-31-akka-23.10-released.html)

## <a href="about:blank#_may_2023"></a> May 2023

- Akka libraries 23.05 releases

  - overview in [Akka 23.05 Released](release-notes/2023-05-16-akka-23.5-released.html)

## <a href="about:blank#_october_2022"></a> October 2022

- Akka libraries 22.10 releases

  - overview in [Akka 22.10 Released](release-notes/2022-10-26-akka-22.10-released.html)

<!-- <footer> -->
<!-- <nav> -->
[Security announcements](security-announcements/index.html) [Support resources](support.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->