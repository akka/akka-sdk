<!-- <nav> -->
- [Akka](../../index.html)
- [Tutorials](../index.html)
- [Multi-agent planner](index.html)
- [Activity agent](activity.html)

<!-- </nav> -->

# Activity agent

|  | **New to Akka? Start here:**

Use the [Build your first agent](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

This guide starts with creating an agent that suggests real-world activities. We will incorporate more components in the next parts of the guide, and at the end we will have a multi-agent system with dynamic planning and orchestration capabilities.

In this part of the guide you will:

- Create a new, empty Akka project
- Create an `Agent`
- Create an HTTP endpoint to expose the agent

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_create_the_empty_project"></a> Create the empty project

1. From a command line, use the Akka CLI to create a new project. See [installation instructions](../quick-install-cli.html) if you haven’t installed the CLI yet.

```command
akka code init --name helloworld-agent --repo akka-samples/empty.git
```
2. Navigate to the new project directory.
3. Open it in your preferred IDE / Editor.
Alternatively, you can clone the [GitHub Repository](https://github.com/akka-samples/empty) directly:

```command
git clone https://github.com/akka-samples/empty.git --depth 1
```
Then navigate to the new project directory and open it in your preferred IDE / Editor.

You can continue from the `helloworld-agent` project.

|  | This guide is written assuming you will follow it as a tutorial to walk through all of the components, building them on your own. If at any time you want to compare your solution with the official sample, check out the [GitHub Repository](https://github.com/akka-samples/multi-agent). |

## <a href="about:blank#_add_the_agent"></a> Add the Agent

We will add an agent that suggests real-world activities. Add a new file `ActivityAgent.java` to `src/main/java/com/example/application/`

ActivityAgent.java
```java
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "activity-agent") // (1)
public class ActivityAgent extends Agent { // (2)

  private static final String SYSTEM_MESSAGE =
    """
    You are an activity agent. Your job is to suggest activities in the
    real world. Like for example, a team building activity, sports, an
    indoor or outdoor game, board games, a city trip, etc.
    """.stripIndent();

  public Effect<String> query(String message) { // (3)
    return effects()
      .systemMessage(SYSTEM_MESSAGE) // (4)
      .userMessage(message) // (5)
      .thenReply();
  }
}
```

| **1** | Annotate the class with `@Component` and pass a unique identifier for this agent type. |
| **2** | Class extends `Agent`. |
| **3** | Define the command handler method. |
| **4** | Define the system message as a constant, and use the system message in the effect builder. |
| **5** | Define the user message for the specific request, and use in the effect builder. |
The system message provides system-level instructions to the AI model that defines its behavior and context. The system message acts as a foundational prompt that establishes the AI’s role, constraints, and operational parameters. It is processed before user messages and helps maintain consistent behavior throughout the interactions.

The user message represents the specific query, instruction, or input that will be processed by the model to generate a response.

## <a href="about:blank#_add_an_endpoint"></a> Add an Endpoint

Add a new file `ActivityEndpoint.java` to `src/main/java/com/example/api/`

ActivityEndpoint.java
```java
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class ActivityEndpoint {

  public record Request(String message) {}

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) { // (1)
    this.componentClient = componentClient;
  }

  @Post("/activities")
  public String suggestActivities(Request request) {
    var sessionId = UUID.randomUUID().toString();
    return componentClient
      .forAgent()
      .inSession(sessionId)
      .method(ActivityAgent::query) // (2)
      .invoke(request.message());
  }
}
```

| **1** | Inject the `ComponentClient`, which is used to call the agent. |
| **2** | Call the `ActivityAgent` created in the previous step |

## <a href="about:blank#_running_the_service"></a> Running the service

Akka has support for many AI providers, and this sample is using OpenAI. This is the configuration:

application.conf
```java
akka.javasdk {
  agent {
    model-provider = openai

    openai {
      model-name = "gpt-4o-mini"
    }
  }
}
```
Set your [OpenAI API key](https://platform.openai.com/api-keys) as an environment variable:

Linux or macOS
```command
export OPENAI_API_KEY=your-openai-api-key
```
Windows 10+
```command
set OPENAI_API_KEY=your-openai-api-key
```
Start your service locally:

```command
mvn compile exec:java
```
Once successfully started, any defined Endpoints become available at `localhost:9000` and you will see an INFO message that the Akka Runtime has started.

In another shell, you can now use `curl` to send requests to this Endpoint.

```command
curl -i -XPOST --location "http://localhost:9000/activities" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do?"}'
```
The AI response might look something like this…​

```none
In Madrid, you can visit historic sites like the Royal Palace, stroll in Retiro Park, explore
the Prado and Reina Sofia art museums, savor local cuisine at Mercado de San Miguel, or take a
guided walking tour through lively neighborhoods such as Malasaña and Chueca.
```

|  | It may take 10s of seconds for the AI model to respond. |

## <a href="about:blank#_next_steps"></a> Next steps

- To make the activity suggestions more personalized, we will add user preferences that the `ActivityAgent` will use. Continue with [User preferences](preferences.html).
- Learn more about the <a href="../../java/agents.html">`Agent` component</a>.

<!-- <footer> -->
<!-- <nav> -->
[Multi-agent planner](index.html) [User preferences](preferences.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->