# Research: Hello World Agent

**Feature**: 001-hello-world-agent
**Date**: 2026-03-01

## Research Tasks

### 1. Agent Session Memory Management

**Decision**: Use Akka's built-in agent session memory with the user name as session ID.

**Rationale**: The Akka Agent framework automatically manages session memory per session ID. By using the `user` field from the request as the session ID (via `.inSession(request.user)`), each user gets independent conversation history without any custom persistence code. The session memory stores all prior messages (system, user, assistant), enabling the LLM to see the full conversation history and avoid repeating languages.

**Alternatives considered**:
- Custom Key Value Entity for session storage — unnecessary overhead; framework handles this automatically
- UUID-based session IDs — would not provide continuity across requests for the same user

### 2. AI Model Provider Configuration

**Decision**: Use OpenAI as the default model provider, configured via `application.conf` with environment variable override for the API key.

**Rationale**: OpenAI's GPT-4o-mini provides good multilingual capabilities at reasonable cost. The configuration supports switching to other providers (Google AI Gemini, Anthropic) by changing `application.conf`. The API key is injected via environment variable (`OPENAI_API_KEY`), validated at startup by the Bootstrap class.

**Alternatives considered**:
- Hardcoded model provider — inflexible, doesn't support different deployment environments
- No startup validation — would lead to confusing runtime errors on first request

### 3. System Prompt Design for Multilingual Greetings

**Decision**: Use a detailed system prompt that instructs the LLM on greeting behavior, language cycling, response format, and conversation style.

**Rationale**: All greeting behavior is driven by the system prompt rather than application code. This keeps the agent implementation minimal (single command handler, no function tools needed). The system prompt specifies: start with English, use different languages each time, append language in parentheses, include English sentences after the greeting, maintain warmth/humor, and append previous greeting history.

**Alternatives considered**:
- Function tools for language tracking — over-engineering; the LLM can track languages from session memory
- Structured response format — unnecessary; plain text response is sufficient for greetings

### 4. Endpoint Design

**Decision**: Single POST `/hello` endpoint accepting `{user, text}` JSON body, returning the greeting as a plain String.

**Rationale**: Simple request/response pattern. The `user` field serves double duty as both user identification and session ID. The response is a plain string (the LLM's greeting text), keeping the API minimal. The endpoint uses `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` for easy demo access.

**Alternatives considered**:
- Separate session management endpoint — unnecessary; sessions are implicit from user name
- Structured JSON response — over-engineering for a greeting demo; plain text suffices
