<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [AI Planner Part 2: User preferences](preferences.html)

<!-- </nav> -->

# AI Planner Part 2: User preferences

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

To make the activity suggestions more personalized, we will add user preferences that the `ActivityAgent` will use.

In this part of the guide you will:

- Create an entity for the preferences
- Use the preferences from the agent
- Include a user id in the endpoint

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_add_the_entity_for_preferences"></a> Add the entity for preferences

Add a new file `PreferencesEntity.java` to `src/main/java/com/example/application/`

PreferencesEntity.java
```java
import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.List;

@ComponentId("preferences") // (2)
public class PreferencesEntity
    extends EventSourcedEntity<Preferences, PreferencesEvent> { // (1)

  public record AddPreference(String preference) {}

  public Effect<Done> addPreference(AddPreference command) { // (3)
    return effects()
        .persist(new PreferencesEvent.PreferenceAdded(command.preference()))
        .thenReply(__ -> Done.done());
  }

  public Effect<Preferences> getPreferences() { // (4)
    List<String> prefs;
    if (currentState() == null) {
      return effects().reply(new Preferences(List.of()));
    } else {
      return effects().reply(currentState());
    }
  }

  @Override
  public Preferences applyEvent(PreferencesEvent event) { // (5)
    return switch (event) {
      case PreferencesEvent.PreferenceAdded evt -> currentState().addPreference(evt.preference());
    };
  }

}
```

| **1** | Extend `EventSourcedEntity`, with the type of state this entity represents, and the interface for the events it persists. |
| **2** | Annotate the class so Akka can identify it as an event-sourced entity. |
| **3** | Define the command handler method to add a preference text. |
| **4** | Define another command handler to retrieve all preferences. |
| **5** | Updates of the `State` is performed from the persisted events. |
Here we use plain text for the preferences, but it could be more structured information.

## <a href="about:blank#_use_from_the_agent"></a> Use from the agent

To use the preferences in the `ActivityAgent` we need to inject the component client and retrieve the preferences for a given user id.

ActivityAgent.java
```java
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.stream.Collectors;

@ComponentId("activity-agent")
public class ActivityAgent extends Agent {

  public record Request(String userId, String message) {}

  private static final String SYSTEM_MESSAGE =
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  private final ComponentClient componentClient;

  public ActivityAgent(ComponentClient componentClient) { // (1)
    this.componentClient = componentClient;
  }

  public Effect<String> query(Request request) { // (2)
    var allPreferences =
      componentClient
          .forEventSourcedEntity(request.userId())
          .method(PreferencesEntity::getPreferences)
          .invoke(); // (3)

    String userMessage;
    if (allPreferences.entries().isEmpty()) {
      userMessage = request.message();
    } else {
      userMessage = request.message() +
          "\nPreferences:\n" +
          allPreferences.entries().stream()
              .collect(Collectors.joining("'\n", "- ", ""));
    }

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userMessage)// (4)
        .thenReply();
  }
}
```

| **1** | Inject `ComponentClient`. |
| **2** | Include user id in the request to the agent. |
| **3** | Retrieve the preferences for the given user id. |
| **4** | In addition to the original message, include the preferences in the user message to the LLM. |

## <a href="about:blank#_user_id_in_endpoint"></a> User id in endpoint

We need to add the user id to the HTTP request.

ActivityEndpoint.java
```java
@Post("/activities/{userId}")
  public String suggestActivities(String userId, Request request) { // (1)
    var sessionId = UUID.randomUUID().toString();
    return componentClient
        .forAgent()
        .inSession(sessionId)
        .method(ActivityAgent::query)
        .invoke(new ActivityAgent.Request(userId, request.message())); // (2)
  }
```

| **1** | Add `userId` as a path parameter. |
| **2** | Call the agent with the `userId`. |

## <a href="about:blank#_update_preferences_from_endpoint"></a> Update preferences from endpoint

To update the preferences, we add another method to the endpoint:

ActivityEndpoint.java
```java
public record AddPreference(String preference) {
  }

  @Post("/preferences/{userId}")
  public HttpResponse addPreference(String userId, AddPreference request) { // (1)
    componentClient
        .forEventSourcedEntity(userId)
        .method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference(request.preference())); // (2)

    return HttpResponses.created();
  }
```

| **1** | Add a method to add a preference. |
| **2** | Call the `PreferenceEntity` for the given user id. |

## <a href="about:blank#_running_the_service"></a> Running the service

Start your service locally:

```command
mvn compile exec:java
```
Pick a user id, here `alice`, and add some preferences:

```command
curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{
    "preference": "I like outdoor activities.",
  }'
```

```command
curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{
    "preference": "I dislike museums.",
  }'
```
Ask for activities.

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do?"}'
```
Does it take your preferences into account for the suggestions?

## <a href="about:blank#_next_steps"></a> Next steps

- Activities may depend on the weather forecast. Continue with [Part 3: Weather agent](weather.html) that will make use of agent function tools.
- Learn more about the <a href="../../java/event-sourced-entities.html">`EventSourceEntity` component</a>.

<!-- <footer> -->
<!-- <nav> -->
[AI Planner Part 1: The activity agent](index.html) [AI Planner Part 3: Weather agent](weather.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
