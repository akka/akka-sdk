# Tasks: Hello World Agent

**Input**: Design documents from `/specs/001-hello-world-agent/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Included — agent test with `TestModelProvider` and endpoint integration test with `httpClient`.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, configuration, and startup validation

- [x] T001 Configure AI model provider settings in `src/main/resources/application.conf` with OpenAI as default, plus Google AI Gemini and Anthropic alternatives. API keys via environment variables.
- [x] T002 Create Bootstrap class implementing `ServiceSetup` in `src/main/java/com/example/Bootstrap.java` that validates the AI model API key is configured at startup (FR-011)

**Checkpoint**: Project compiles and fails fast if API key is missing (`mvn compile`)

---

## Phase 2: User Story 1 & 2 - Multilingual Greeting Agent (Priority: P1) 🎯 MVP

**Goal**: A user sends a message and receives a cheerful greeting in a specific language. The agent cycles through different languages across interactions, maintaining session memory, and appends a list of previous greetings.

**Independent Test**: Send POST `/hello` with `{user, text}` and verify response contains a greeting with language in parentheses, English sentences, and previous greeting history.

**Covers**: US1 (basic greeting), US2 (language cycling with memory), US3 (contextual conversation continuity), US4 (independent user sessions via session ID)

### Implementation

- [x] T003 [US1] Create HelloWorldAgent extending `Agent` with `@Component(id = "hello-world-agent")` in `src/main/java/com/example/application/HelloWorldAgent.java`. Implement single `greet(String)` command handler using `effects().systemMessage(...).userMessage(...).thenReply()`. System prompt must instruct LLM to: start with English, use different language each time, append language in parentheses (FR-004, FR-005, FR-006), include English sentences (FR-007), relate to previous interactions (FR-008), append previous greetings list (FR-009), respond with enthusiasm and humor (FR-010).
- [x] T004 [US1] Create HelloWorldEndpoint with `@HttpEndpoint` and `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` in `src/main/java/com/example/api/HelloWorldEndpoint.java`. Define inner `Request(String user, String text)` record. Implement `POST /hello` that routes to `HelloWorldAgent::greet` using `request.user` as session ID via `componentClient.forAgent().inSession(request.user)` (FR-001, FR-002, FR-003).

**Checkpoint**: Service compiles and responds to POST `/hello` with multilingual greetings (`mvn compile`)

### Tests

- [x] T005 [US1] Create agent integration test in `src/test/java/com/example/HelloWorldAgentIntegrationTest.java` using `TestKitSupport` and `TestModelProvider`. Override `testKitSettings()` to register mock model with fake API key config. Test that agent returns a greeting response via `componentClient.forAgent().inSession("test-session").method(HelloWorldAgent::greet).invoke(...)`. Verify response is not empty.
- [x] T006 [US1] Create endpoint integration test in `src/test/java/com/example/HelloWorldEndpointIntegrationTest.java` using `TestKitSupport` and `TestModelProvider`. Test POST `/hello` with `httpClient.POST("/hello").withRequestBody(new Request("alice", "Hello")).responseBodyAs(String.class).invoke()`. Verify successful response status and non-empty body.

**Checkpoint**: All tests pass (`mvn verify`)

---

## Phase 3: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and quickstart validation

- [x] T007 Update `README.md` with curl examples matching the HTTP API contract: first greeting, second greeting (different language), and different user (independent session)
- [x] T008 Validate quickstart by running through `specs/001-hello-world-agent/quickstart.md` scenarios manually

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **User Story 1 & 2 (Phase 2)**: Depends on Phase 1 completion
- **Polish (Phase 3)**: Depends on Phase 2 completion

### User Story Dependencies

- **US1 (Greeting + Response)** and **US2 (Language Cycling)**: Implemented together — both are driven by the agent's system prompt and session memory. Single agent component covers both.
- **US3 (Contextual Continuity)**: Achieved through the system prompt instructions in T003 (FR-008). No additional component needed.
- **US4 (Independent Sessions)**: Achieved through the endpoint design in T004 (FR-003) — using `request.user` as session ID. No additional component needed.

### Within Phase 2

- T003 (Agent) before T004 (Endpoint) — endpoint depends on agent
- T004 (Endpoint) before T005, T006 (Tests) — tests depend on components existing
- T005 and T006 can run in parallel (different test files)

### Parallel Opportunities

- T001 and T002 can run in parallel (different files)
- T005 and T006 can run in parallel (different test files)

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Agent + Endpoint (T003-T004)
3. **STOP and VALIDATE**: Test with curl against running service
4. Add tests (T005-T006) and verify with `mvn verify`

### Incremental Delivery

1. Setup → Configuration and startup validation ready
2. Agent + Endpoint → Full greeting functionality available (MVP!)
3. Tests → Automated validation
4. Polish → Documentation complete

---

## Notes

- All greeting behavior (language cycling, format, tone, previous greeting tracking) is driven by the LLM system prompt — no application-level state management needed
- Session memory is automatic via Akka agent framework — using `user` as session ID provides per-user isolation
- US1-US4 are all covered by the same 2 components (agent + endpoint) due to the simplicity of this feature
- Total: 8 tasks, 3 components (Bootstrap, Agent, Endpoint), 2 test files
