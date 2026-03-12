# Data Model: Consulting — Delegation + Handoff

**Feature**: 005-consulting | **Date**: 2026-03-12

## Domain Records

### ConsultingResult

The typed result for the ENGAGEMENT task. Produced by either the coordinator (standard) or senior consultant (complex).

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `assessment` | `String` | Problem assessment | Non-empty |
| `recommendation` | `String` | Recommended course of action | Non-empty |
| `escalated` | `boolean` | Whether the problem was handed off to a senior | true = handoff, false = delegation |

**Package**: `demo.consulting.domain`

```java
public record ConsultingResult(String assessment, String recommendation, boolean escalated) {}
```

### ResearchSummary

The typed result for the RESEARCH task. Produced by the ConsultingResearcher.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `topic` | `String` | The research topic | Non-empty |
| `findings` | `String` | Research findings | Non-empty |

**Package**: `demo.consulting.domain`

```java
public record ResearchSummary(String topic, String findings) {}
```

## Task Definitions

### ConsultingTasks

| Task | Name | Result Type | Description |
|------|------|-------------|-------------|
| `ENGAGEMENT` | `"Engagement"` | `ConsultingResult` | Top-level consulting engagement |
| `RESEARCH` | `"Research"` | `ResearchSummary` | Delegated research sub-task |

**Package**: `demo.consulting.application`

## Tools

### ConsultingTools

Shared domain tools for consistent problem assessment across agents.

| Method | Description | Returns |
|--------|-------------|---------|
| `assessProblem(String)` | Preliminary assessment of the problem | Assessment string |
| `checkComplexity(String)` | Determines if problem exceeds standard scope | "COMPLEX: ..." or "STANDARD: ..." |

**Package**: `demo.consulting.application`

Complexity triggers: problems mentioning "regulatory", "m&a", "merger", "acquisition", "compliance" → COMPLEX.

## Components

### ConsultingCoordinator (AutonomousAgent)

| Property | Value |
|----------|-------|
| Component ID | `"consulting-coordinator"` |
| Accepts | `ConsultingTasks.ENGAGEMENT` |
| Max iterations | `10` |
| Tools | `ConsultingTools` |
| Delegation | `ConsultingResearcher.class` |
| Handoff | `SeniorConsultant.class` |

### ConsultingResearcher (AutonomousAgent)

| Property | Value |
|----------|-------|
| Component ID | `"consulting-researcher"` |
| Description | "Performs targeted research on specific aspects of consulting problems" |
| Accepts | `ConsultingTasks.RESEARCH` |
| Max iterations | `3` |

### SeniorConsultant (AutonomousAgent)

| Property | Value |
|----------|-------|
| Component ID | `"senior-consultant"` |
| Description | "Handles complex, high-stakes consulting issues requiring senior expertise" |
| Accepts | `ConsultingTasks.ENGAGEMENT` |
| Max iterations | `5` |
| Tools | `ConsultingTools` |

### ConsultingEndpoint (HTTP Endpoint)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/engagements` | Submit a consulting problem, returns task ID |
| `GET` | `/engagements/{taskId}` | Poll for engagement result |

## API Types

### EngagementRequest

| Field | Type | Description |
|-------|------|-------------|
| `problem` | `String` | The consulting problem description |

### EngagementResponse

| Field | Type | Description |
|-------|------|-------------|
| `taskId` | `String` | Identifier for polling |

### ConsultingResultResponse

| Field | Type | Description |
|-------|------|-------------|
| `assessment` | `String` | Problem assessment |
| `recommendation` | `String` | Recommendation |
| `escalated` | `Boolean` | Whether handed off to senior |
| `status` | `String` | Task status |

**Package**: `demo.consulting.api` (as inner records of endpoint)

## Entity Relationship

```
Client --[POST /engagements]--> ConsultingEndpoint
  --> creates ConsultingCoordinator (UUID)
  --> runSingleTask(ENGAGEMENT.instructions(problem))
  --> returns taskId

ConsultingCoordinator
  --> assessProblem() + checkComplexity()
  --> if STANDARD: delegateToConsultingResearcher(RESEARCH task)
      --> ConsultingResearcher produces ResearchSummary
      --> Coordinator synthesises ConsultingResult(escalated=false)
  --> if COMPLEX: handoffToSeniorConsultant(ENGAGEMENT task)
      --> SeniorConsultant produces ConsultingResult(escalated=true)

Client --[GET /engagements/{taskId}]--> ConsultingEndpoint
  --> componentClient.forTask(ENGAGEMENT).get(taskId)
  --> returns ConsultingResultResponse
```
