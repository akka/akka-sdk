<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Testing](testing.html)

<!-- </nav> -->

# Testing the agent

Testing agents built with Generative AI involves two complementary approaches: evaluating the quality of the non-deterministic model behavior and writing deterministic unit tests for the agent’s and surrounding components' logic. Evaluations is described in [LLM evaluation](llm_eval.html), and here we will cover the deterministic testing.

## <a href="about:blank#_mocking_responses_from_the_model"></a> Mocking responses from the model

For predictable and repeatable tests of your agent’s business logic and component integrations, it’s essential to use deterministic responses. This allows you to verify that your agent behaves correctly when it receives a known model output.

Use the `TestKitSupport` and the `ComponentClient` to call the components from the test. The `ModelProvider` of the agents can be replaced with [TestModelProvider](_attachments/testkit/akka/javasdk/testkit/TestModelProvider.html), which provides ways to mock the responses without using the real AI model.

[AgentTeamWorkflowTest.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/test/java/demo/multiagent/application/AgentTeamWorkflowTest.java)
```java
public class AgentTeamWorkflowTest extends TestKitSupport { // (1)

  private final TestModelProvider selectorModel = new TestModelProvider(); // (2)
  private final TestModelProvider plannerModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider summaryModel = new TestModelProvider();
  private final TestModelProvider toxicityEvalModel = new TestModelProvider();
  private final TestModelProvider summarizationEvalModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(SelectorAgent.class, selectorModel) // (3)
      .withModelProvider(PlannerAgent.class, plannerModel)
      .withModelProvider(ActivityAgent.class, activitiesModel)
      .withModelProvider(WeatherAgent.class, weatherModel)
      .withModelProvider(SummarizerAgent.class, summaryModel)
      .withModelProvider(ToxicityEvaluator.class, toxicityEvalModel)
      .withModelProvider(SummarizationEvaluator.class, summarizationEvalModel);
  }

  @Test
  public void test() {
    var selection = new AgentSelection(List.of("activity-agent", "weather-agent"));
    selectorModel.fixedResponse(JsonSupport.encodeToString(selection)); // (4)

    var weatherQuery = "What is the current weather in Stockholm?";
    var activityQuery =
      "Suggest activities to do in Stockholm considering the current weather.";
    var plan = new Plan(
      List.of(
        new PlanStep("weather-agent", weatherQuery),
        new PlanStep("activity-agent", activityQuery)
      )
    );
    plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

    weatherModel
      .whenMessage(req -> req.equals(weatherQuery)) // (5)
      .reply("The weather in Stockholm is sunny.");

    activitiesModel
      .whenMessage(req -> req.equals(activityQuery))
      .reply(
        "You can take a bike tour around Djurgården Park, " +
        "visit the Vasa Museum, explore Gamla Stan (Old Town)..."
      );

    summaryModel.fixedResponse(
      "The weather in Stockholm is sunny, so you can enjoy " +
      "outdoor activities like a bike tour around Djurgården Park, " +
      "visiting the Vasa Museum, exploring Gamla Stan (Old Town)"
    );

    toxicityEvalModel.fixedResponse(
      """
      {
        "label" : "non-toxic"
      }
      """.stripIndent()
    );

    summarizationEvalModel.fixedResponse(
      """
      {
        "label" : "good"
      }
      """.stripIndent()
    );

    var query = "I am in Stockholm. What should I do? Beware of the weather";

    var sessionId = UUID.randomUUID().toString();
    var request = new AgentTeamWorkflow.Request("alice", query);

    componentClient
      .forWorkflow(sessionId)
      .method(AgentTeamWorkflow::start) // (6)
      .invoke(request);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, SECONDS)
      .untilAsserted(() -> {
        var answer = componentClient
          .forWorkflow(sessionId)
          .method(AgentTeamWorkflow::getAnswer)
          .invoke();
        assertThat(answer).isNotBlank();
        assertThat(answer).contains("Stockholm");
        assertThat(answer).contains("sunny");
        assertThat(answer).contains("bike tour");
      });
  }
}
```

| **1** | Extend `TestKitSupport` to gain access to testing utilities for Akka components. |
| **2** | Create one or more `TestModelProvider`. Using one per agent allows for distinct mock behaviors, while sharing one is useful for testing general responses. |
| **3** | Use the settings of the `TestKit` to replace the agent’s real `ModelProvider` with your test instance. |
| **4** | For simple tests, define a single, fixed response that the mock model will always return. |
| **5** | For more complex scenarios, define a response that is only returned if the user message matches a specific condition. This is useful for testing different logic paths within your agent. |
| **6** | Call the components with the `componentClient`. |

## <a href="about:blank#_mocked_model_in_a_deployed_service"></a> Mocked model in a deployed service

In some scenarios it can be useful to run the service deployed but without interacting with an actual agent. For example,
a load test that exercises the service with heavy load to verify scalability could quickly consume a large number of tokens
when the exact answer from the model is not very important, one or a few different predefined responses and responding with
a slight delay to simulate model processing time could be good enough.

It is possible to implement a custom model provider using `akka.javasdk.agent.ModelProvider.Custom`, such a mock provider
however, side steps quite a bit of the infrastructure involved in agent interactions, a more realistic mock model can be implemented
by building a separate Akka service with a single [HTTP endpoint](../http-endpoints.html) mimicking the model endpoint and
configuring the deployed agentic service to use that.

Here is an example endpoint returning a static response over the OpenAI protocol:

[MockOpenAI.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/MockOpenAI.java)
```java
@HttpEndpoint
@Acl(allow = { @Acl.Matcher(service = "*") })
public class MockOpenAI extends AbstractHttpEndpoint {

  private static final long MIN_DELAY_MILLIS = 2000;
  private static final long MAX_DELAY_MILLIS = 3000;
  private static final long DELAY_SPAN = MAX_DELAY_MILLIS - MIN_DELAY_MILLIS;

  private static final HttpResponse staticResponse = HttpResponse.create()
    .withStatus(StatusCodes.OK)
    .withEntity(
      HttpEntities.create(
        ContentTypes.APPLICATION_JSON,
        """
        { "id": "chatcmpl-Byz9msOuInWGiYmFJR8eH7ei2S3d0",
          "object": "chat.completion",
          "created": 1753874466,
          "model": "gpt-4o-mini-2024-07-18",
           "choices": [
           {
             "index": 0,
             "message": {
               "role": "assistant",
               "content": "Some hardcoded result",
               "refusal": null,
               "annotations": []
             },
             "logprobs": null,
             "finish_reason": "stop"
           }],
           "usage": {
             "prompt_tokens": 29,
             "completion_tokens": 264,
             "total_tokens": 293,
             "prompt_tokens_details": {
               "cached_tokens": 0,
               "audio_tokens": 0
             },
             "completion_tokens_details": {
               "reasoning_tokens": 0,
               "audio_tokens": 0,
               "accepted_prediction_tokens": 0,
               "rejected_prediction_tokens": 0
             }
           },
           "service_tier": "default",
           "system_fingerprint": "fp_197a02a720"
        }"""
      )
    )
    .withHeaders(
      Arrays.asList(
        RawHeader.create("x-request-id", "537dc248-255e-49eb-8799-fcc11a8b6cf0"),
        RawHeader.create("x-ratelimit-limit-tokens", "2000000"),
        RawHeader.create("openai-organization", "abc-123123"),
        RawHeader.create("openai-version", "20200-01"),
        RawHeader.create("openai-processing-ms", "5916"),
        RawHeader.create("openai-project", "proj_1234567abcdef")
      )
    );

  @Post("/chat/completions")
  public HttpResponse completion(HttpEntity.Strict ignoredRequestBody) throws Exception {
    var delay = MIN_DELAY_MILLIS + ThreadLocalRandom.current().nextLong(DELAY_SPAN);
    Thread.sleep(delay);
    return staticResponse;
  }
}
```
For more elaborate scenarios, the mock model endpoint may have to parse the request to decide which hard coded answer out of a few
or to create a reply in a more dynamic fashion.

Deploying this service as `mock-openai` allows other services containing agents in the same [Akka project](../../operations/projects/index.html).
Using the deployed mock service from an agent in another service can be done with a config like this:

application.conf
```hocon
akka.javasdk {
  agent {
    model-provider = openai

    openai {
      model-name = "gpt-4o-mini"
      base-url = "http://mock-openai" // (1)
    }
  }
}
```

1. The service name the mock was deployed as.
Note that you should use `http`, and not `https`, the connection will be encrypted with TLS, but that is handled by the platform.

## <a href="about:blank#_log_model_request_and_response"></a> Log model request and response

To see exactly what is sent to and received from the AI model, you can enable the following logger in `include-dev-loggers.xml`:

```none
<logger name="kalix.runtime.agent.AkkaLangChain4jHttpClient" level="TRACE"/>
```

<!-- <footer> -->
<!-- <nav> -->
[LLM evaluation](llm_eval.html) [Event Sourced Entities](../event-sourced-entities.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->