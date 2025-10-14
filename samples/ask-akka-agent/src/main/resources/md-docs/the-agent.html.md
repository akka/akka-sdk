<!-- <nav> -->
- [Akka](../../index.html)
- [Tutorials](../index.html)
- [RAG chat agent](index.html)
- [Creating the agent](the-agent.html)

<!-- </nav> -->

# Creating the agent

|  | **New to Akka? Start here:**

Use the [Build your first agent](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

This guide starts with creating an agent. We will incorporate Retrieval-Augmented Generation (RAG) in the next steps.

In this part of the guide you will:

- Create a new, empty Akka project
- Create an `Agent`
- Create an HTTP endpoint to expose the agent

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_unfamiliar_with_concepts_like_vectors_embeddings_or_rag"></a> Unfamiliar with concepts like vectors, embeddings or RAG?

We recommend reviewing our [foundational explainer on AI concepts](../../concepts/ai-agents.html#_foundational_ai_concepts_video). It offers helpful background that will deepen your understanding of the technologies and patterns used throughout this tutorial.

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

|  | This guide is written assuming you will follow it as a tutorial to walk through all of the components, building them on your own. If at any time you want to compare your solution with the official sample, check out the [GitHub Repository](https://github.com/akka-samples/ask-akka-agent). |

## <a href="about:blank#_add_the_agent"></a> Add the Agent

Add a new file `AskAkkaAgent.java` to `src/main/java/akka/ask/agent/application/`

AskAkkaAgent.java
```java
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
  id = "ask-akka-agent", // (2)
  name = "Ask Akka",
  description = "Expert in Akka")
public class AskAkkaAgent extends Agent { // (1)

  private static final String SYSTEM_MESSAGE =
    """
    You are a very enthusiastic Akka representative who loves to help people!
    Given the following sections from the Akka SDK documentation, answer the question
    using only that information, outputted in markdown format.
    If you are unsure and the text is not explicitly written in the documentation, say:
    Sorry, I don't know how to help with that.
    """.stripIndent(); // (4)

  public StreamEffect ask(String question) { // (3)
    return streamEffects()
      .systemMessage(SYSTEM_MESSAGE) // (4)
      .userMessage(question) // (5)
      .thenReply();
  }
}
```

| **1** | Create a class that extends `Agent`. |
| **2** | Make sure to annotate such class with `@Component` and pass a unique identifier for this agent type. |
| **3** | Define the command handler method. |
| **4** | Define the system message. |
| **5** | Define the user message for the specific request. |
The system message provides system-level instructions to the AI model that defines its behavior and context. The system message acts as a foundational prompt that establishes the AI’s role, constraints, and operational parameters. It is processed before user messages and helps maintain consistent behavior throughout the interactions.

The user message represents the specific query, instruction, or input that will be processed by the model to generate a response.

For this agent we want immediate visual feedback, and use the `StreamEffecct` to be able to stream the response to the client using server-sent events (SSE).

## <a href="about:blank#_add_an_endpoint"></a> Add an Endpoint

Add a new file `AskHttpEndpoint.java` to `src/main/java/akka/ask/agent/api/`

[AskHttpEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/api/AskHttpEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api")
public class AskHttpEndpoint {

  public record QueryRequest(String userId, String sessionId, String question) {}

  private final ComponentClient componentClient;

  public AskHttpEndpoint(ComponentClient componentClient) { // (1)
    this.componentClient = componentClient;
  }

  /**
   * This method runs the search and streams the response to the UI.
   */
  @Post("/ask")
  public HttpResponse ask(QueryRequest request) {
    var sessionId = request.userId() + "-" + request.sessionId();
    var responseStream = componentClient
      .forAgent()
      .inSession(sessionId)
      .tokenStream(AskAkkaAgent::ask)
      .source(request.question); // (2)

    return HttpResponses.serverSentEvents(responseStream); // (3)
  }
}
```

| **1** | Inject the `ComponentClient`, which is used to call the agent. |
| **2** | Call the `AskAkkaAgent created in the previous step |
| **3** | Use the `serverSentEvents` function to easily send a stream via SSE |
The `userId` and `sessionId` parameters are required in `QueryRequest` along with the `question` field.

## <a href="about:blank#_running_the_service"></a> Running the service

Akka has support for many AI providers, and this sample is using OpenAI. This is the configuration:

[application.conf](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/resources/application.conf)
```java
akka.javasdk {
  agent {
    # Other AI models can be configured, see https://doc.akka.io/java/agents.html#model
    # and https://doc.akka.io/java/model-provider-details.html for the reference configurations.
    model-provider = openai

    openai {
      model-name = "gpt-4o-mini"
      # Environment variable override for the API key
      api-key = ${?OPENAI_API_KEY}
    }
  }
}

mongodb.uri = "mongodb://user:pass@localhost:27019/?directConnection=true"
mongodb.uri = ${?MONGODB_ATLAS_URI}
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
curl localhost:9000/api/ask --header "Content-Type: application/json" -XPOST \
--data '{ "userId": "001", "sessionId": "foo", \
          "question":"What are the core components of Akka?"}'
```
The AI response will look something like this…​

```none
Akka is a toolkit for building highly concurrent, distributed,
and resilient message-driven applications...
1. Actor System ...
2. Actors ...
...
```
This is correct for the Akka libraries, but we want to know about the components in the Akka SDK. We need to give the LLM knowledge about the latest Akka documentation, which is the reason for adding Retrieval-Augmented Generation (RAG) to the agent.

## <a href="about:blank#_next_steps"></a> Next steps

It’s time to explore our first aspect of the agentic RAG flow: [Knowledge indexing](indexer.html).

<!-- <footer> -->
<!-- <nav> -->
[RAG chat agent](index.html) [Knowledge indexing with a workflow](indexer.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->