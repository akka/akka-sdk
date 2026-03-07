# Tasks: Pet Store

**Input**: Design documents from `/specs/001-pet-store/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/http-api.md

**Tests**: Included per constitution Principle III (Test Coverage) and CLAUDE.md incremental workflow.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. Within each story, tasks follow the CLAUDE.md incremental workflow: Domain → Entity → Unit Test → View/Workflow/Agent → Integration Test → Endpoint → Endpoint Integration Test.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Base package**: `src/main/java/com/example/petstore/`
- **Test package**: `src/test/java/com/example/petstore/`
- **Resources**: `src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project structure, package initialization, and configuration

- [x] T001 Create package structure: `com.example.petstore.{domain,application,api}` under `src/main/java/` and `src/test/java/`
- [x] T002 Update `pom.xml` to change groupId to `com.example.petstore` and artifactId to `pet-store`
- [x] T003 Configure `src/main/resources/application.conf` with Akka settings and AI model provider placeholder

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared domain records that multiple user stories depend on

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 [P] Create Pet state record with lifecycle logic and Status enum in `src/main/java/com/example/petstore/domain/Pet.java`
- [x] T005 [P] Create PetEvent sealed interface with all events (Registered, MadeAvailable, Reserved, Adopted, Released, Removed) with `@TypeName` annotations in `src/main/java/com/example/petstore/domain/PetEvent.java`

**Checkpoint**: Foundation ready — domain records compiled, user story implementation can begin

---

## Phase 3: User Story 2 — Manage Pet Lifecycle (Priority: P1)

**Goal**: Admins can register pets, update status through lifecycle, and enforce valid transitions

**Independent Test**: Register a pet, transition through Registered → Available → Reserved → Adopted, verify invalid transitions rejected

### Implementation for User Story 2

- [x] T006 [US2] Create PetEntity (Event Sourced Entity) with command handlers for register, makeAvailable, reserve, adopt, release, remove, and getPet in `src/main/java/com/example/petstore/application/PetEntity.java`
- [x] T007 [US2] Create PetEntityTest with unit tests using EventSourcedTestKit covering all command handlers and invalid transitions in `src/test/java/com/example/petstore/application/PetEntityTest.java`
- [ ] T008 [US2] Create PetEndpoint with admin routes: POST /pets, GET /pets/{petId}, PUT /pets/{petId}/make-available, PUT /pets/{petId}/remove in `src/main/java/com/example/petstore/api/PetEndpoint.java`
- [ ] T009 [US2] Create PetEndpointIntegrationTest for admin routes using httpClient in `src/test/java/com/example/petstore/api/PetEndpointIntegrationTest.java`

**Checkpoint**: Pet lifecycle fully functional via HTTP API — register, status transitions, and rejection of invalid transitions verified

---

## Phase 4: User Story 1 — Browse and Find Available Pets (Priority: P1)

**Goal**: Users can query all available pets and filter by species

**Independent Test**: Register pets, make them available, query via view — verify only available pets returned and species filter works

### Implementation for User Story 1

- [ ] T010 [US1] Create AvailablePetsBySpeciesView with TableUpdater consuming PetEvent and queries for all available pets and by species in `src/main/java/com/example/petstore/application/AvailablePetsBySpeciesView.java`
- [ ] T011 [US1] Create AvailablePetsViewIntegrationTest using TestKitSupport with event publishing and Awaitility for view queries in `src/test/java/com/example/petstore/api/AvailablePetsViewIntegrationTest.java`
- [ ] T012 [US1] Update PetEndpoint with catalog routes: GET /pets/available and GET /pets/available?species={species} in `src/main/java/com/example/petstore/api/PetEndpoint.java`
- [ ] T013 [US1] Update PetEndpointIntegrationTest with catalog route tests (register pet, make available, query via endpoint) in `src/test/java/com/example/petstore/api/PetEndpointIntegrationTest.java`

**Checkpoint**: Available pets catalog fully queryable via HTTP — species filter verified

---

## Phase 5: User Story 3 — Shopping Cart (Priority: P2)

**Goal**: Users can add/remove available pets to a temporary cart and view cart contents

**Independent Test**: Create cart, add pets, remove a pet, verify cart contents

### Implementation for User Story 3

- [ ] T014 [US3] Create ShoppingCart state record with addPet, removePet, and duplicate-prevention logic in `src/main/java/com/example/petstore/domain/ShoppingCart.java`. Note: cart only holds pet IDs; adoption endpoint reads cart contents to initiate workflow, cart itself has no checkout step
- [ ] T015 [US3] Create ShoppingCartEntity (Key Value Entity) with command handlers for addPet, removePet, getCart in `src/main/java/com/example/petstore/application/ShoppingCartEntity.java`
- [ ] T016 [US3] Create ShoppingCartEntityTest with unit tests using KeyValueEntityTestKit covering add, remove, duplicate rejection in `src/test/java/com/example/petstore/application/ShoppingCartEntityTest.java`
- [ ] T017 [US3] Create ShoppingCartEndpoint with routes: POST /carts/{cartId}/add, POST /carts/{cartId}/remove, GET /carts/{cartId} in `src/main/java/com/example/petstore/api/ShoppingCartEndpoint.java`
- [ ] T018 [US3] Create ShoppingCartEndpointIntegrationTest using httpClient in `src/test/java/com/example/petstore/api/ShoppingCartEndpointIntegrationTest.java`

**Checkpoint**: Shopping cart fully functional via HTTP — add, remove, view, and duplicate prevention verified

---

## Phase 6: User Story 4 — Adopt a Pet (Priority: P2)

**Goal**: Users can initiate adoption from cart, reserve pets, confirm with adopter info, or cancel (releasing pets)

**Independent Test**: Add pets to cart, start adoption (pets reserved), confirm adoption (pets marked adopted), verify cancel returns pets to available

### Implementation for User Story 4

- [ ] T019 [US4] Create Adoption state record with status enum (Started, PetsReserved, Confirmed, Cancelled, Failed) and transition methods in `src/main/java/com/example/petstore/domain/Adoption.java`
- [ ] T020 [US4] Create AdoptionWorkflow with command handlers (start, confirm, cancel, getStatus), steps (reservePetsStep, confirmAdoptionStep, cancelAdoptionStep with compensation), and WorkflowSettings with 30-minute step timeout for reservation + step recovery failover to cancelAdoptionStep in `src/main/java/com/example/petstore/application/AdoptionWorkflow.java`
- [ ] T021 [US4] Create AdoptionEndpoint with routes: POST /adoptions, PUT /adoptions/{id}/confirm, PUT /adoptions/{id}/cancel, GET /adoptions/{id} in `src/main/java/com/example/petstore/api/AdoptionEndpoint.java`
- [ ] T022 [US4] Create AdoptionEndpointIntegrationTest covering full adoption flow (start, confirm), cancellation flow, and concurrent adoption rejection using httpClient in `src/test/java/com/example/petstore/api/AdoptionEndpointIntegrationTest.java`

**Checkpoint**: Adoption workflow fully functional — reserve, confirm, cancel with compensation, and concurrent safety verified

---

## Phase 7: User Story 5 — AI Pet Recommendation Assistant (Priority: P3)

**Goal**: Users can chat with an AI agent that recommends available pets based on preferences

**Independent Test**: Start a chat session, send preferences, verify agent returns recommendation referencing available pets

### Implementation for User Story 5

- [ ] T023 [US5] Create PetRecommendationAgent with single command handler and @FunctionTool for querying available pets view in `src/main/java/com/example/petstore/application/PetRecommendationAgent.java`
- [ ] T024 [US5] Create PetRecommendationAgentIntegrationTest using TestModelProvider with fixedResponse in `src/test/java/com/example/petstore/application/PetRecommendationAgentIntegrationTest.java`
- [ ] T025 [US5] Create PetAssistantEndpoint with route: POST /assistant/chat accepting sessionId and message in `src/main/java/com/example/petstore/api/PetAssistantEndpoint.java`
- [ ] T026 [US5] Create PetAssistantEndpointIntegrationTest using httpClient and TestModelProvider in `src/test/java/com/example/petstore/api/PetAssistantEndpointIntegrationTest.java`

**Checkpoint**: AI assistant functional via HTTP — chat with recommendations referencing actual inventory

---

## ~~Phase 8: User Story 6 — Pet Store Website~~ *(Deferred)*

> Web UI deferred to a future iteration. All functionality testable via HTTP API.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and final validation

- [ ] T027 Update README.md with project description, build instructions, and curl examples from quickstart.md
- [ ] T028 Run full test suite (`mvn verify`) and validate all integration tests pass
- [ ] T029 Run quickstart.md validation — execute all curl commands and verify responses

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **US2 (Phase 3)**: Depends on Phase 2 — PetEntity needed by US1, US4
- **US1 (Phase 4)**: Depends on Phase 3 (needs PetEntity for view to consume events and endpoint to exist)
- **US3 (Phase 5)**: Depends on Phase 2 only — can run in parallel with US1/US2 if needed
- **US4 (Phase 6)**: Depends on Phase 3 (needs PetEntity reserve/adopt/release commands) and Phase 5 (needs cart checkout)
- **US5 (Phase 7)**: Depends on Phase 4 (needs AvailablePetsBySpeciesView for agent tool)
- **US6 (Deferred)**: Web UI deferred to future iteration
- **Polish (Phase 8)**: Depends on all active phases complete

### User Story Dependencies

- **US2 (P1)**: Foundational entity — must be built first. Provides PetEntity commands used by US1, US4.
- **US1 (P1)**: Depends on US2 (view consumes PetEntity events, endpoint extends PetEndpoint catalog routes)
- **US3 (P2)**: Independent of US1/US2 — only needs foundational domain. Can be parallelized.
- **US4 (P2)**: Depends on US2 (PetEntity reserve/adopt/release) and US3 (cart checkout)
- **US5 (P3)**: Depends on US1 (agent tool queries AvailablePetsBySpeciesView)
- **US6 (P3)**: *(Deferred)* — Web UI out of scope for this iteration

### Within Each User Story

- Domain records before entities/workflows
- Entities/workflows before tests
- Tests verified before endpoints
- Endpoints before endpoint integration tests
- All tests passing before moving to next story

### Parallel Opportunities

- T004 and T005 (foundational domain records) can run in parallel
- US3 (Shopping Cart) can run in parallel with US2/US1 if another developer is available
- Within US5: T023 and T025 touch different files but T025 depends on T023

---

## Implementation Strategy

### MVP First (US2 + US1)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational domain records
3. Complete Phase 3: US2 — Pet lifecycle management with entity + tests + endpoint
4. Complete Phase 4: US1 — Available pets view + catalog endpoint
5. **STOP and VALIDATE**: Register pets, make available, query catalog via API
6. Deploy/demo if ready — functional pet catalog

### Incremental Delivery

1. Setup + Foundational → Base ready
2. US2 (Pet Lifecycle) → Admin can manage pets
3. US1 (Browse Pets) → Users can discover available pets → **MVP!**
4. US3 (Shopping Cart) → Users can build selections
5. US4 (Adoption) → Users can complete adoptions
6. US5 (AI Assistant) → Users get AI recommendations
7. ~~US6 (Website)~~ → Deferred
8. Polish → Documentation and final validation

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Follow CLAUDE.md incremental workflow: present each component for user approval before proceeding
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- US2 before US1 because PetEntity is the foundation the view consumes from
