# Research: Pet Store

**Branch**: `001-pet-store` | **Date**: 2026-03-01

## Component Mapping Decisions

### Pet Entity → Event Sourced Entity

- **Decision**: Use Event Sourced Entity for Pet lifecycle management
- **Rationale**: Pet status transitions (Registered → Available → Reserved → Adopted/Removed) need an audit trail. Event sourcing captures the full history of each pet's lifecycle.
- **Alternatives considered**:
  - Key Value Entity: Simpler but loses event history. Rejected because views need to consume events, and audit trail is valuable for adoption records.

### Shopping Cart → Key Value Entity

- **Decision**: Use Key Value Entity for Shopping Cart
- **Rationale**: Cart is a temporary selection with simple CRUD operations (add/remove pet IDs). No need for event history — only current state matters.
- **Alternatives considered**:
  - Event Sourced Entity: Overkill for temporary state. Cart doesn't need audit trail.

### Adoption Process → Workflow

- **Decision**: Use Workflow for adoption orchestration
- **Rationale**: Adoption is a multi-step process (reserve pets → collect adopter info → confirm/cancel) with compensation needs (cancel returns pets to Available). Workflow provides step sequencing, timeouts, and recovery.
- **Alternatives considered**:
  - Direct entity calls from endpoint: No compensation, no timeout management, no step tracking. Rejected.

### Available Pets Query → View

- **Decision**: Use View consuming from PetEntity events
- **Rationale**: View provides eventually-consistent query model. Consumes PetEvent to build queryable index of available pets filterable by species.
- **Alternatives considered**:
  - Direct entity reads: Would require knowing all pet IDs. Not feasible for catalog browsing.

### AI Recommendation → Agent

- **Decision**: Use Agent with function tools for pet recommendations
- **Rationale**: Agent wraps LLM interaction with structured tool access. A `@FunctionTool` can query the available pets view to provide real inventory data to the LLM.
- **Alternatives considered**:
  - Direct LLM API call from endpoint: Loses session memory, tool integration, and Akka's built-in retry/error handling.

### Web UI → Static resources served from HTTP Endpoint

- **Decision**: Serve a simple web UI via Akka's static resource support
- **Rationale**: `HttpResponses.staticResource()` can serve HTML/JS/CSS. Keeps the project self-contained without a separate frontend build.
- **Alternatives considered**:
  - Separate frontend project: More complex, out of scope for initial implementation. Can be added later.

## Package Structure

```
com.example.petstore.domain/
  - Pet.java (state record)
  - PetEvent.java (sealed interface)
  - ShoppingCart.java (state record)
  - Adoption.java (state record)

com.example.petstore.application/
  - PetEntity.java (Event Sourced Entity)
  - ShoppingCartEntity.java (Key Value Entity)
  - AdoptionWorkflow.java (Workflow)
  - AvailablePetsBySpeciesView.java (View)
  - PetRecommendationAgent.java (Agent)

com.example.petstore.api/
  - PetEndpoint.java (HTTP - admin + catalog)
  - ShoppingCartEndpoint.java (HTTP - cart operations)
  - AdoptionEndpoint.java (HTTP - adoption process)
  - PetAssistantEndpoint.java (HTTP - AI chat)

src/main/resources/static/
  - index.html (web UI)
```

## Technology Decisions

- **Akka SDK**: 3.5.14 (current parent POM version)
- **AI Model**: Configured via `application.conf`, default OpenAI provider
- **Frontend**: Minimal HTML/JS served as static resources
- **Session ID strategy**: UUID for new chat sessions, workflow ID for adoption-related agent calls
