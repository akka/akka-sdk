# Multi-agent playground

This project is a development playground for the multi-agent orchestration.

## Agents

Four delegative agents are available, each orchestrating a set of specialist agents:

- **triage-agent** — routes requests to the appropriate specialist agent, chains agents for complex tasks
- **language-triage-agent** — detects the language of a request and delegates to the English or Spanish agent
- **activity-planner-agent** — plans activities by coordinating weather, activities, restaurants, transport, events, and budget agents
- **content-creation-agent** — full pipeline: research → write → edit → evaluate, with iterative revision cycles
- **content-refinement-agent** — writer/critic feedback loop for iterative content refinement

## Running

```shell
mvn compile exec:java
```

## Example curl commands

All endpoints are under `http://localhost:9000/agent`. Each request requires a `sessionId` (any unique string) to identify the agent session.

---

### Triage agent

Start a session — the triage agent routes to the right specialist agents and chains them if needed:

```shell
curl -X POST http://localhost:9000/agent/triage/my-session-1 \
  -H "Content-Type: application/json" \
  -d '{"message": "Plan a weekend trip to Barcelona and write a travel blog about it"}'
```

Check status / retrieve result:

```shell
curl http://localhost:9000/agent/triage/my-session-1
```

---

### Activity planner agent

Start a session:

```shell
curl -X POST http://localhost:9000/agent/activity-planner/my-session-2 \
  -H "Content-Type: application/json" \
  -d '{"message": "Suggest outdoor activities for a team of 10 in London next Saturday, budget £50 per person"}'
```

Check status / retrieve result:

```shell
curl http://localhost:9000/agent/activity-planner/my-session-2
```

---

### Content creation agent

Start a session (research-backed long-form content):

```shell
curl -X POST http://localhost:9000/agent/content-creation/my-session-3 \
  -H "Content-Type: application/json" \
  -d '{"message": "Write a blog post about the impact of AI on software development, technical tone"}'
```

Check status / retrieve result:

```shell
curl http://localhost:9000/agent/content-creation/my-session-3
```

---

### Language triage agent

Start a session — detects the language and delegates to the English or Spanish agent:

```shell
curl -X POST http://localhost:9000/agent/language-triage/my-session-5 \
  -H "Content-Type: application/json" \
  -d '{"message": "¿Cuáles son las mejores actividades para hacer en Madrid?"}'
```

```shell
curl -X POST http://localhost:9000/agent/language-triage/my-session-6 \
  -H "Content-Type: application/json" \
  -d '{"message": "What are the best activities to do in London?"}'
```

Check status / retrieve result:

```shell
curl http://localhost:9000/agent/language-triage/my-session-5
```

---

### Content refinement agent

Start a session (writer/critic loop for short-form content):

```shell
curl -X POST http://localhost:9000/agent/content-refinement/my-session-4 \
  -H "Content-Type: application/json" \
  -d '{"message": "Write a LinkedIn post announcing our new AI-powered product launch, professional and engaging tone"}'
```

Check status / retrieve result:

```shell
curl http://localhost:9000/agent/content-refinement/my-session-4
```
