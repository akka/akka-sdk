# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

See @README.md for a project overview, and how to build, test and run the application.

## Coding guidelines

Use the detailed instructions in @AGENTS.md when writing Akka code.

Use the guidelines in @akka-context/java/ai-coding-assistant-guidelines.html.md when writing code in this project.

## Akka documentation

You find the reference documentation of Akka in the akka-context directory and sub-directories.
Read this documentation to answer questions about Akka.

## Incremental Generation Workflow

**Work step-by-step with user approval between major phases. Keep explanations brief - developers prefer concise communication.**

**CRITICAL: After completing each step below, you MUST STOP and WAIT for explicit user approval before proceeding to the next step. When you ask "Ready for X?", you are NOT allowed to continue until the user responds. NEVER create code for the next step until the user says "yes", "proceed", or similar.**

Create one component and a corresponding test at a time, with user feedback in between. If there are several components involved in the task some steps below should be repeated for each component. For example: create an entity, create unit test for the entity, create view, create test for the view, create endpoint for the entity and view, create integration test for the endpoint.

1. **Always wait** for user approval between major steps - this is MANDATORY
2. **If user says "proceed"/"yes","y"**, continue to next step
3. **If user provides feedback**, adjust and re-present
4. **If user says "skip X"**, skip that step
5. **Keep focused** - don't jump ahead, don't create multiple steps at once
6. **Brief explanations** - what you did, not verbose details

This approach enables early validation, catches issues before coding, and allows mid-course adjustments.

### Step 0: Documentation Check (complex components only)

For first-time or complex components:
1. Read relevant `akka-context/java/*.html.md`
2. Extract code examples, verify imports/patterns
3. Brief note: "📚 Reviewed {doc}"

### Step 1: Design & Planning

Present concise design:
```markdown
## Proposed Design

### Components
- Entity: CreditCardEntity (Event Sourced - need audit trail)
- View: CreditCardsByCardholderView
- Endpoint: CreditCardEndpoint

### Domain Model
**CreditCard (state):** cardId, cardNumber, cardholderName, creditLimit, currentBalance, active

**Events:** CardActivated, CardCharged, PaymentMade, CardBlocked

**Commands:** activate, charge, makePayment, block

### Integration
- View consumes: CardActivated, CardCharged, PaymentMade, CardBlocked
- Endpoint exposes: POST /cards/{id}/activate, POST /cards/{id}/charge, GET /cards/by-cardholder/{name}

Does this design look good? Should I proceed?
```

**STOP and wait for user approval**

### Step 2: Domain Layer

Create domain classes. Verify with `mvn compile`.

Report briefly:
```markdown
Created domain layer:
- domain/CreditCard.java (4 events)
- domain/CreditCardEvent.java

Ready for Entity?
```

**STOP and wait for user approval**

### Step 3: Application Layer

Create one component at a time, with user feedback in between.

Create application components. Verify with `mvn compile`.

Report briefly:
```markdown
Created application layer entity:
- application/CreditCardEntity.java (5 command handlers)

Please review.

Ready for tests?
```

**STOP and wait for user approval**

### Step 4: Tests

Create one test at a time, with user feedback in between.

Create tests for the component in the previous step. Verify with `mvn test` or with `mvn verify` for IntegrationTest.

Report briefly:
```markdown
Created tests:
- CreditCardEntityTest.java (8 test cases)

Please review.

Ready for API layer?
```

**STOP and wait for user approval**

### Step 5: API Layer

Create/update endpoint. Verify with `mvn compile`.

Report briefly:
```markdown
Updated API layer:
- api/CreditCardEndpoint.java
  - POST /cards/{id}/activate
  - POST /cards/{id}/charge
  - GET /cards/by-cardholder/{name}

Please review.

Ready for integration tests?
```

**STOP and wait for user approval**

### Step 6: API Layer integration tests

Create tests for the endpoint in the previous step. Verify with `mvn verify`.

Report briefly:
```markdown
Created tests:
- CreditCardEndpointIntegrationTest.java (6 test cases)

Please review.

Do you also want me to update the readme and include example curl commands of the endpoint?
```

**STOP and wait for user approval**

### Step 7: Documentation

```markdown
Updated README.md with curl examples.

Done! Created 2 domain classes, 1 entity, 1 view, 3 tests, 1 endpoint.
```
