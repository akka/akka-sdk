# A2A agents

A sample of two Akka services that talk to each other over the Agent2Agent (A2A) protocol.

- `claims-service` publishes two skills on an A2A endpoint. `claim-status` is a short skill answered by an agent. `review-claim` is a long skill backed by a workflow that asks for the policy number when the message has none.
- `claims-assistant` calls `claims-service`. An agent offers the `claim-status` skill to its model as a tool. A workflow step calls the `review-claim` skill and follows the remote task with `notifyWorkflow`.

The two services are separate Maven projects. Each runs on its own.

## Akka components

`claims-service`:

- **Agent (`ClaimStatusAgent`)**: Looks up the status of a claim with a function tool. `@A2aSkill` publishes it as the `claim-status` skill.
- **Workflow (`ClaimReviewWorkflow`)**: Reviews a claim over several steps and reports its progress with `A2aTaskContext`. `@A2aSkill` publishes it as the `review-claim` skill.
- **A2A endpoint (`ClaimsA2aEndpoint`)**: Lists the agent and the workflow. Akka serves the Agent Card at `/.well-known/agent-card.json` and the A2A routes at `/a2a`.

`claims-assistant`:

- **Agent (`ClaimsAssistantAgent`)**: Passes the skills of the remote agent `claims` to its model as tools with `a2aAgents`.
- **Workflow (`ClaimReviewRequestWorkflow`)**: Sends a message to the `review-claim` skill with `A2aClient` and waits for the updates of the remote task.
- **HTTP endpoint (`ClaimsAssistantEndpoint`)**: Exposes `/assistant` and `/reviews`.

The remote agent `claims` is configured in `claims-assistant/src/main/resources/application.conf` under `akka.javasdk.a2a.remote-agents`.

## Running locally

Both services call an AI model, so set an API key first:

```shell
export OPENAI_API_KEY=your-key-here
```

Start `claims-service`, which listens on port 9000:

```shell
cd claims-service
mvn compile exec:java
```

In a second terminal, start `claims-assistant`, which listens on port 9001:

```shell
cd claims-assistant
mvn compile exec:java
```

Read the Agent Card of the claims service:

```shell
curl http://localhost:9000/.well-known/agent-card.json
```

Ask the assistant for the status of a claim. The assistant calls the `claim-status` skill:

```shell
curl -i -X POST http://localhost:9001/assistant \
  --header "Content-Type: application/json" \
  --data '{"message": "What is the status of claim 42?"}'
```

Request a review. The workflow calls the `review-claim` skill and follows the remote task:

```shell
curl -i -X POST http://localhost:9001/reviews/42 \
  --header "Content-Type: application/json" \
  --data '{"policyNumber": "P-1234"}'

curl http://localhost:9001/reviews/42
```

## Tests

Run `mvn verify` in each service. The tests use the model mock of the TestKit, so they call no model. The tests of `claims-assistant` use `MockA2aAgent` as the remote agent.

## Documentation

- [Designing A2A endpoints](https://doc.akka.io/sdk/a2a-endpoints.html)
- [Remote A2A agents](https://doc.akka.io/sdk/agents/remote-a2a-agents.html)
