# Data Model: Hello World Agent

**Feature**: 001-hello-world-agent
**Date**: 2026-03-01

## Overview

This feature has a minimal data model. There are no persistent entities, events, or state machines. The agent is stateless, and conversation history is managed automatically by the Akka agent session memory framework.

## API Types

### Request

**HelloWorldEndpoint.Request**
- `user` (String, required) — User identifier, also used as the agent session ID
- `text` (String, required) — The user's message text

### Response

Plain `String` — The agent's greeting response containing:
1. A greeting phrase in a specific language with language name in parentheses
2. One or more conversational sentences in English
3. A list of all previous greetings from the session

## Agent Session Memory

Session memory is managed by the Akka framework. Each session (keyed by `user`) stores:
- All system messages (the greeting system prompt)
- All user messages sent in the session
- All assistant (agent) responses

No custom data model or persistence code is required.

## Configuration

**application.conf** defines:
- `akka.javasdk.agent.model-provider` — AI model provider (openai, googleai-gemini, anthropic)
- `akka.javasdk.agent.openai.api-key` — API key from environment variable `OPENAI_API_KEY`
- `akka.javasdk.agent.openai.model-name` — Model name (default: gpt-4o-mini)
- Similar blocks for alternative providers (Google AI Gemini, Anthropic)
