# HTTP API Contract: Hello World Agent

**Feature**: 001-hello-world-agent
**Date**: 2026-03-01

## POST /hello

Send a message to the greeting agent and receive a multilingual greeting response.

### Request

```
POST /hello
Content-Type: application/json
```

**Body**:
```json
{
  "user": "alice",
  "text": "Hello, I am Alice"
}
```

| Field  | Type   | Required | Description |
|--------|--------|----------|-------------|
| `user` | String | Yes      | User identifier. Used as the agent session ID for conversation continuity. |
| `text` | String | Yes      | The user's message to the greeting agent. |

### Response

**Success (200 OK)**:
```
Content-Type: text/plain
```

Plain text greeting from the agent. Example first response:
```
Hello there, Alice! (English)

Welcome! It's wonderful to meet you! I'm thrilled to be your multilingual greeting companion. 🌍

Previous greetings:
- Hello (English)
```

Example second response:
```
Bonjour, Alice! (French)

Ah, it's lovely to see you again! Since we last spoke, I thought we'd take a little trip to France today. How magnifique!

Previous greetings:
- Hello (English)
- Bonjour (French)
```

**Error (500)**:
Returned when the AI model is unavailable or misconfigured.

### Session Behavior

- The `user` field determines the session. Same `user` value = same conversation history.
- Different `user` values = completely independent sessions.
- First interaction always starts with an English greeting.
- Subsequent interactions cycle through different languages.

### Access Control

- Endpoint is publicly accessible (`principal = INTERNET`).
- No authentication required.
