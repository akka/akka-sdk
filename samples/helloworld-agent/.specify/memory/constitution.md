<!--
Sync Impact Report
- Version change: 1.0.0 → 2.0.0
- Redefined principles:
  - I. Akka SDK First (broadened from Domain Design)
  - II. Design Principles (merged Domain Purity + API Isolation + new items)
  - III. Test Coverage (high-level quality gate, no implementation details)
  - IV. Simplicity (streamlined)
- Removed from v1:
  - Incremental Generation Workflow (conflicts with speckit.implement)
  - Akka SDK Conventions (coding rules, belongs in AGENTS.md)
  - Technology Constraints / Development Workflow (folded or moved to AGENTS.md)
- Removed sections:
  - Technology Constraints (brief version folded into Akka SDK First)
  - Development Workflow (replaced with reference to AGENTS.md/CLAUDE.md)
- Templates requiring updates:
  - .specify/templates/plan-template.md — ✅ compatible (Constitution Check section is generic)
  - .specify/templates/spec-template.md — ✅ compatible (requirements section is generic)
  - .specify/templates/tasks-template.md — ✅ compatible (testing phases compatible)
- Follow-up TODOs: none
-->

# Akka Constitution

## Core Principles

### I. Akka SDK First (NON-NEGOTIABLE)

Every feature MUST be built on the Akka SDK. All service components,
state management, event handling, and inter-service communication MUST
use Akka SDK primitives (Entities, Views, Workflows, Agents, Consumers,
Endpoints) rather than custom or third-party alternatives.

- Deviating from Akka SDK patterns requires explicit justification
  documented in the implementation plan.
- External dependencies beyond the Akka SDK dependency tree MUST be
  justified in the implementation plan before being added.
- Before adding a dependency, evaluate whether the functionality
  can be achieved with the Akka SDK, the standard library, or
  a small amount of application code.

### II. Design Principles

These principles shape how specifications decompose features into
components and guide architectural decisions.

- **Domain independence**: Domain logic MUST be independent of framework
  concerns, enabling isolated testing and reuse.
- **API isolation**: Endpoints MUST define their own request/response
  types rather than exposing domain internals.
- **Single responsibility**: Each component MUST have a clear, focused
  purpose. Prefer multiple small components over monolithic ones.
- **Descriptive naming**: Names MUST be domain-aligned and descriptive.
  Avoid generic names like `Event`, `Service`, or `Manager`.

### III. Test Coverage

Every behavioral change MUST be accompanied by tests. Specifications
and task plans MUST include explicit testing phases.

- Unit tests MUST cover all business logic and domain rules.
- Each component MUST have corresponding unit or integration tests.
- Test names MUST describe the behavior under test, not the
  implementation.
- Test coverage MUST NOT decrease with any change; new code MUST
  include corresponding tests before merge.

### IV. Simplicity

Build only what is needed now. Complexity compounds over time.

- YAGNI: Do not build features, abstractions, or extension points for
  hypothetical future requirements.
- Prefer flat, direct code over deeply nested abstractions.
- If a simpler solution meets the requirement, it MUST be chosen over
  a more sophisticated one.

## Coding Conventions

Detailed coding rules, component patterns, naming conventions, and
testing patterns are maintained in AGENTS.md. This file is the
authoritative source for implementation-level guidance and MUST be
consulted when writing code.

Akka reference documentation in the `akka-context/` directory MUST
be consulted when planning and implementing components, especially
for first-time or complex component types.

## Governance

This constitution is the authoritative source for architectural
principles. It governs how specifications and plans are structured.

- **Supremacy**: When a conflict arises between this constitution and
  other project documents, the constitution prevails for architectural
  decisions.
- **Compliance**: Specifications and implementation plans MUST verify
  alignment with these principles.
- **Amendment Process**: Changes MUST be documented with a version
  increment (MAJOR for principle removal/redefinition, MINOR for new
  principles, PATCH for clarifications) and rationale.

**Version**: 2.0.0 | **Ratified**: 2026-03-01 | **Last Amended**: 2026-03-04
