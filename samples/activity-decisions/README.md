# Activity decisions

A sample that tries the OpenAI Decisions API as the decision model of the `DecisionModelClient`. It uses the activity domain of the agent guide: a workflow suggests real-world activities for a user, with a weather agent and an activity agent.

The decision model chooses the steps of the workflow:

1. **classify.** The workflow asks the decision model two questions about the user's message. The choice question `topic` selects `activities`, `weather` or `other`. The predicate question `needs-weather` gives the probability that good suggestions depend on the weather.
   - `other` ends the workflow with a fixed answer, without calling an agent.
   - `weather` asks the weather agent and ends with its answer.
   - `activities` asks the weather agent first when `needs-weather` is at least 0.5, and then the activity agent.
2. **check.** The workflow asks the decision model about the suggestion of the activity agent. The input is a JSON object with the request, the preferences, the weather and the suggestion. The score question `quality` rates the suggestion from 0 to 2. The predicate question `respects-preferences` is only asked when the user has preferences.
   - A quality below 1.5 or a preference probability below 0.5 sends the suggestion back to the activity agent once, with feedback.
   - Otherwise the suggestion is the answer.

The result of a workflow holds every decision of the decision model, with the step that asked for it. The service log has a line per decision as well.

## Build against unreleased snapshots

The OpenAI decision model provider is not released. The sample needs a local runtime snapshot and a local SDK snapshot. `pom.xml` uses the SDK version that the following steps produce.

1. Publish the runtime. In `akka-runtime`, on the branch `wip-openai-decisions-patriknw` (commit `fd97e8bd8`):

   ```shell
   sbt publishLocal publishM2
   ```

   The version is `1.7.6-14-fd97e8bd-SNAPSHOT`.

2. Publish the SDK. In `akka-sdk`, on the branch `wip-openai-decisions-patriknw` (commit `70611a294`), with a clean working tree:

   ```shell
   sbt -Dakka-runtime.version=1.7.6-14-fd97e8bd-SNAPSHOT -Dakka.javasdk.no-discipline=true publishM2
   git restore artifact-bom
   ```

   The version is `3.7.2-M1-32-70611a29-SNAPSHOT`. If sbt prints another version, set that version as the parent version in `pom.xml`. The compile rewrites the BOM files under `artifact-bom/`, and `git restore` resets them.

3. Switch back to the branch of this sample.

## Run

```shell
export OPENAI_API_KEY=your-openai-api-key
# Optional. Without it, the weather agent uses fake weather.
export WEATHER_API_KEY=your-weather-api-key
mvn compile exec:java
```

The agents and the decision model use the same `OPENAI_API_KEY`. `application.conf` selects `gpt-4o-mini` for the agents and `gpt-6-luna` for the decision model.

Add a preference:

```shell
curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"preference": "I do not like museums."}'
```

Ask for activities. The response body is the workflow id:

```shell
curl -i localhost:9000/activities/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"message": "I am in Stockholm. What should I do?"}'
```

Read the answer and the decisions:

```shell
curl localhost:9000/activities/alice/<workflow id> | jq
```

The answer is empty while the workflow runs. A failed decision model call ends the workflow, and `failure` names the step, the exception and its message.

Some messages to try:

- "I am in Stockholm. What should I do?" asks for activities and depends on the weather.
- "Suggest a board game for four people." asks for activities that do not depend on the weather.
- "Will it rain in Madrid today?" asks only for the weather.
- "What is the capital of Sweden?" is about something else.

## Test

```shell
mvn verify
```

The integration test answers the decision requests with the `TestDecisionModelProvider` of the testkit, so it needs no API key.
