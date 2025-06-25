<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [AI Planner Part 3: Weather agent](weather.html)

<!-- </nav> -->

# AI Planner Part 3: Weather agent

[1: The activity agent](index.html) > [2: User preferences](preferences.html) > **Weather agent** > [4: Orchestrate the agents](team.html) > [5: List by user](list.html) > [6: Dynamic orchestration](dynamic-team.html)

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

Activities may depend on the weather, so let’s add an agent that retrieves a weather forecast.

In this part of the guide you will:

- Create an agent for the weather forecast that uses an external service as a function tool

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_add_the_agent"></a> Add the agent

Add a new file `WeatherAgent.java` to `src/main/java/com/example/application/`

WeatherAgent.java
```java
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ComponentId("weather-agent")
public class WeatherAgent extends Agent {

  private static final String SYSTEM_MESSAGE = // (1)
      """
      You are a weather agent.
      Your job is to provide weather information.
      You provide current weather, forecasts, and other related information.
      
      The responses from the weather services are in json format. You need to digest
      it into human language. Be aware that Celsius temperature is in temp_c field.
      Fahrenheit temperature is in temp_f field.
      """.stripIndent();

  private final HttpClient httpClient;

  public WeatherAgent(HttpClientProvider httpClientProvider) { // (2)
    this.httpClient = httpClientProvider.httpClientFor("https://api.weatherapi.com");
  }

  public Effect<String> query(String message) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(message)
        .thenReply();
  }

  @FunctionTool(description = "Returns the current weather forecast for a given city.")
  private String getCurrentWeather( // (3)
      @Description("A location or city name.")
      String location) {

    var date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

    var encodedLocation = java.net.URLEncoder.encode(location, StandardCharsets.UTF_8);
    var apiKey = System.getenv("WEATHER_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      throw new RuntimeException("Make sure you have WEATHER_API_KEY defined as environment variable.");
    }

    String url = String.format("/v1/current.json?&q=%s&aqi=no&key=%s&dt=%s",
        encodedLocation, apiKey, date);
    return httpClient.GET(url).invoke().body().utf8String();
  }
}
```

| **1** | Instructions for the weather agent. |
| **2** | Inject the `HttpClientProvider`. |
| **3** | Provide the weather forecast as a function tool. |
Methods annotated with `@FunctionTool` in the agent will automatically be made available to the AI model, which will extract the location from the original query and request to execute the tool to retrieve the forecast.

If you don’t want to use the real weather service, you can change the implementation to return a hard-coded weather, such as `"It’s always sunny"`.

We could make a request to the `WeatherAgent` from the endpoint before calling the `ActivityAgent` but a better approach is to introduce a workflow that orchestrates the calls between the agents.

## <a href="about:blank#_test_the_agent"></a> Test the agent

Before introducing the workflow we would like to see that the `WeatherAgent` works in isolation.

To use the preferences from the `ActivityAgent` we need to inject the component client and retrieve the preferences for a given user id.

ActivityAgent.java
```java
import akka.javasdk.testkit.TestKitSupport;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class WeatherAgentIntegrationTest extends TestKitSupport { // (1)

  public void testAgent() {
    var sessionId = UUID.randomUUID().toString();
    var message = "I am in Madrid";
    var forecast =
        componentClient
        .forAgent()
        .inSession(sessionId)
        .method(WeatherAgent::query) // (2)
        .invoke(message);

    System.out.println(forecast); // (3)
    assertThat(forecast).isNotBlank();
  }
}
```

| **1** | Extend `TestKitSupport`. |
| **2** | Use the component client to call the agent. |
| **3** | Not much we can assert, since the weather is different every day, but at least we can see the result and that it doesn’t fail. |
You can sign up for a free API for the weather service at [https://www.weatherapi.com](https://www.weatherapi.com/) and then expose it as an environment variable:

Linux or macOS
```command
export WEATHER_API_KEY=your-openai-api-key
```
Windows 10+
```command
set WEATHER_API_KEY=your-openai-api-key
```
Run the test with

```command
mvn verify
```

## <a href="about:blank#_next_steps"></a> Next steps

- Introduce a workflow that orchestrates the calls between the agents. Continue with [Part 4: Orchestrate the agents](team.html)
- Learn more about more possibilities of [extending agents with function tools](../../java/agents.html#tools).

<!-- <footer> -->
<!-- <nav> -->
[AI Planner Part 2: User preferences](preferences.html) [AI Planner Part 4: Orchestrate the agents](team.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->