# Quickstart: Hello World Agent

**Feature**: 001-hello-world-agent
**Date**: 2026-03-01

## Prerequisites

- Java 21+
- Maven
- An OpenAI API key (or alternative AI model provider key)

## Setup

1. Set your OpenAI API key:
   ```shell
   export OPENAI_API_KEY=your-openai-api-key
   ```

2. Build the project:
   ```shell
   mvn compile
   ```

3. Start the service locally:
   ```shell
   mvn compile exec:java
   ```

## Try It Out

### Send your first greeting

```shell
curl -i -XPOST http://localhost:9000/hello \
  --header "Content-Type: application/json" \
  --data '{"user": "alice", "text": "Hello, I am Alice"}'
```

Expected: A greeting in English with warmth and enthusiasm.

### Send a second message (different language)

```shell
curl -i -XPOST http://localhost:9000/hello \
  --header "Content-Type: application/json" \
  --data '{"user": "alice", "text": "I love traveling to new places"}'
```

Expected: A greeting in a different language (e.g., Spanish, French), with a reference to Alice's previous introduction and the conversation topic. Previous greetings listed at the end.

### Try a different user (independent session)

```shell
curl -i -XPOST http://localhost:9000/hello \
  --header "Content-Type: application/json" \
  --data '{"user": "bob", "text": "Hi there, I am Bob"}'
```

Expected: A greeting in English (Bob's first interaction — independent of Alice's session).

## Alternative Model Providers

Edit `src/main/resources/application.conf` to switch providers:

- **Google AI Gemini**: Set `model-provider = googleai-gemini` and `export GOOGLE_AI_GEMINI_API_KEY=...`
- **Anthropic**: Set `model-provider = anthropic` and `export ANTHROPIC_API_KEY=...`

## Deployment

Build the container image:
```shell
mvn clean install -DskipTests
```

Set up the secret and deploy:
```shell
akka secret create generic openai-api --literal key=$OPENAI_API_KEY
akka service deploy helloworld-agent helloworld-agent:tag-name --push \
  --secret-env OPENAI_API_KEY=openai-api/key
```
