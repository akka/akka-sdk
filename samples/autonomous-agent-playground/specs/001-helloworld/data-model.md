# Data Model: Hello World Autonomous Agent

**Feature**: 001-helloworld | **Date**: 2026-03-12

## Domain Records

### Answer

The typed result produced by the QuestionAnswerer agent.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `answer` | `String` | The agent's response to the question | Non-empty |
| `confidence` | `int` | Confidence score for the answer | 0–100 |

**Package**: `demo.helloworld.domain`

```java
public record Answer(String answer, int confidence) {}
```

## Task Definitions

### QuestionTasks

Immutable task definition constants for the helloworld sample.

| Task | Name | Result Type | Description |
|------|------|-------------|-------------|
| `ANSWER` | `"Answer"` | `Answer` | Answer a question with a typed result |

**Package**: `demo.helloworld.application`

```java
public class QuestionTasks {
  public static final Task<Answer> ANSWER = Task
    .define("Answer")
    .description("Answer a question clearly and concisely, providing a confidence score")
    .resultConformsTo(Answer.class);
}
```

## API Types

### QuestionRequest

HTTP request body for submitting a question.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `question` | `String` | The question to answer | Non-empty, non-blank |

### QuestionResponse

HTTP response body after submitting a question.

| Field | Type | Description |
|-------|------|-------------|
| `taskId` | `String` | Identifier for polling the result |

### AnswerResponse

HTTP response body when retrieving a completed result.

| Field | Type | Description |
|-------|------|-------------|
| `answer` | `String` | The agent's answer |
| `confidence` | `int` | Confidence score (0–100) |
| `status` | `String` | Task status: "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED" |

**Package**: `demo.helloworld.api` (as inner records of the endpoint)

## Components

### QuestionAnswerer (AutonomousAgent)

| Property | Value |
|----------|-------|
| Component ID | `"question-answerer"` |
| Accepts | `QuestionTasks.ANSWER` |
| Max iterations | `3` |
| Tools | None |
| Coordination | None |

### QuestionEndpoint (HTTP Endpoint)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/questions` | Submit a question, returns task ID |
| `GET` | `/questions/{taskId}` | Poll for result |

## Entity Relationship

```
User --[POST /questions]--> QuestionEndpoint
  --> creates QuestionAnswerer instance (UUID)
  --> runSingleTask(ANSWER.instructions(question))
  --> returns taskId

User --[GET /questions/{taskId}]--> QuestionEndpoint
  --> componentClient.forTask(ANSWER).get(taskId)
  --> returns TaskSnapshot<Answer> mapped to AnswerResponse
```
