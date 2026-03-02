<!--
Sync Impact Report
- Version change: 0.0.0 (template) → 1.0.0
- Added principles:
  - I. Domain Design
  - II. Incremental Generation Workflow
  - III. Test Coverage
  - IV. Akka SDK Conventions
  - V. Simplicity & Minimalism
- Added sections:
  - Technology Constraints
  - Development Workflow
- Removed sections: none (initial creation)
- Templates requiring updates:
  - .specify/templates/plan-template.md — ✅ compatible (Constitution Check section aligns)
  - .specify/templates/spec-template.md — ✅ compatible (user stories + requirements align)
  - .specify/templates/tasks-template.md — ✅ compatible (phase structure aligns)
- Follow-up TODOs: none
-->

# Akka Service Constitution

## Core Principles

### I. Domain Design

- Domain records MUST reside in the `domain` package with zero Akka SDK dependencies.
- Domain records MUST be immutable Java records containing their own business logic
  (validation, mutation via `with*` methods).
- Domain records MUST NOT emit effects or return event lists; effects belong
  exclusively to the `application` package.
- Package structure MUST follow `[org].[app-name].[module].{domain|application|api}`.
  No dependency from `domain` to `application`, or from `application` to `api`.
- Events MUST be defined as records implementing a sealed interface with `@TypeName`
  annotations (e.g., `CreditCardEvent` with `CardCharged`, `PaymentMade`).
- State records MUST use `with*` builder-style methods for immutable field updates.

### II. Incremental Generation Workflow

- Every feature MUST follow the step-by-step workflow defined in CLAUDE.md:
  Design, Domain, Application (one component at a time), Tests, API, Integration Tests, Docs.
- The AI assistant MUST stop and wait for explicit user approval between each major step.
- Each component and its corresponding test MUST be created and validated before
  proceeding to the next component.
- All code MUST compile (`mvn compile`) before presenting it for review.
- Tests MUST pass (`mvn test` or `mvn verify`) before proceeding to the next step.

### III. Test Coverage

- Entity unit tests MUST use `EventSourcedTestKit` or `KeyValueEntityTestKit`.
- View integration tests MUST use `TestKitSupport` with event publishing
  and `Awaitility.await()` for eventual consistency assertions.
- Endpoint integration tests MUST use `httpClient` (never `componentClient`).
- Agent tests MUST use `TestModelProvider` with `.fixedResponse()` or `.whenMessage()`.
- Integration test classes MUST have the `IntegrationTest` suffix.
- Tests MUST use JUnit 5+ annotations and AssertJ `assertThat`.

### IV. Akka SDK Conventions

- Imports MUST use `akka.*` (never `io.akka.*`).
- Components MUST use `@Component(id = "...")` (not deprecated `@ComponentId`).
- HTTP/gRPC endpoints MUST NOT have `@Component` annotations.
- HTTP endpoints MUST have `@Acl` annotations.
- Endpoints MUST return API-specific types (never domain records directly).
- Endpoints MUST use synchronous style with `.invoke()` (not `.invokeAsync()`).
- `ComponentClient` MUST only be injected into Endpoints, Agents, Consumers,
  TimedActions, Workflows, and ServiceSetup (never into Entities or Views).
- Event Sourced Entity views MUST use `onEvent()` (never `onUpdate()`).
- Workflows MUST use `settings()` with method references for steps
  (never deprecated `definition()`).
- Agents MUST have exactly one command handler method.

### V. Simplicity & Minimalism

- Code MUST favor functional and fluent styles over imperative loops.
- Only changes directly requested or clearly necessary MUST be made;
  no speculative features, extra configurability, or premature abstractions.
- Business logic MUST reside in domain objects; entities MUST only orchestrate
  effects based on domain validation results.
- Command handlers MUST accept 0 or 1 parameter. Multiple parameters
  MUST be wrapped in a single command record.
- Empty command records without fields MUST NOT be created; use parameterless
  handler methods instead.

## Technology Constraints

- **Language**: Java (records for data, sealed interfaces for events)
- **Framework**: Akka SDK 3.5+ (parent POM `akka-javasdk-parent`)
- **Build**: Maven (`mvn compile`, `mvn test`, `mvn verify`)
- **Testing**: JUnit 5, AssertJ, Awaitility, Akka TestKit
- **Package convention**: `com.[org].[app-name].{domain|application|api}`
- **Deployment**: Akka Console / `akka` CLI

## Development Workflow

- Follow the Incremental Generation Workflow (Principle II) for all features.
- Read relevant `akka-context/sdk/*.html.md` documentation before coding
  first-time or complex components (especially Workflows and Agents).
- Run the self-review checklist from AGENTS.md before presenting any code.
- Commit after each logical step or task group.
- Update README.md with curl examples after endpoint creation.

## Governance

- This constitution supersedes conflicting practices found elsewhere in the project.
- Amendments MUST be documented with version bump, rationale, and migration plan.
- Versioning follows semantic versioning:
  MAJOR for principle removals/redefinitions, MINOR for new principles/sections,
  PATCH for clarifications and wording fixes.
- All code reviews and AI-generated code MUST verify compliance with these principles.
- Complexity beyond these principles MUST be justified in a Complexity Tracking table
  (see plan template).
- Runtime development guidance is maintained in CLAUDE.md and AGENTS.md.

**Version**: 1.0.0 | **Ratified**: 2026-03-01 | **Last Amended**: 2026-03-01
