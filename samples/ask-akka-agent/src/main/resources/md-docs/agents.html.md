<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [Agents](agents.html)

<!-- </nav> -->

# Implementing agents

![Agent](../_images/agent.png)
An Agent interacts with an AI model to perform a specific task. It is typically backed by a large language model (LLM). It maintains contextual history in a session memory, which may be shared between multiple agents that are collaborating on the same goal. It may provide function tools and call them as requested by the model.

## <a href="about:blank#_identify_the_agent"></a> Identify the agent

Every component in Akka needs to be identifiable by the rest of the system. This usually involves two different forms of identification: a **component ID** an **instance ID**. We use component IDs as a way to identify the component *class* and distinguish it from others. Instance identifiers are, as the name implies, unique identifiers for an instance of a component.

As with all other components, we supply an identifier for the component class using the `@Component` annotation.

In the case of agents, we don’t supply a unique identifier for the instance of the agent. Instead, we supply an identifier for the *session* to which the agent is bound. This lets you have multiple components with different component IDs all performing various agentic tasks within the same shared session.

## <a href="about:blank#_effect_api"></a> Agent’s effect API

Effects are declarative in nature. When components handle commands, they can return an `Effect`. Some components can produce only a few effects while others, such as the Agent, can produce a wide variety.

The Agent’s Effect defines the operations that Akka should perform when an incoming command is handled by an Agent. These effects can be any of the following:

- declare which model will be used
- specify system messages, user messages and additional context (prompts)
- configure session memory
- define available tools
- fail a command by returning an error
- return an error message
- transform responses from a model and reply to incoming commands
For additional details, refer to [Declarative Effects](../concepts/declarative-effects.html).

## <a href="about:blank#_skeleton"></a> Skeleton

An agent implementation has the following code structure.

[MyAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/MyAgent.java)
```java
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "my-agent") // (2)
public class MyAgent extends Agent { // (1)

  public Effect<String> query(String question) { // (3)
    return effects().systemMessage("You are a helpful...").userMessage(question).thenReply();
  }
}
```

| **1** | Create a class that extends `Agent`. |
| **2** | Make sure to annotate the class with `@Component` and pass a unique identifier for this agent type. |
| **3** | Define the command handler method. |

|  | The `@Component` value `my-agent` is common for all instances of this agent and must be unique across the different components in the service. |
An agent must have one command handler method that is public and returns `Effect<T>`, where `T` it the type of the reply. Alternatively it can return `StreamEffect` for [streaming responses](agents/streaming.html).

Command handlers in Akka may take one or no parameters as input. If you need multiple parameters for a command, you can wrap them in a record class and pass an instance of that to the command handler as the sole parameter.

There can only be one command handler because the agent is supposed to perform one single well-defined task.

## <a href="about:blank#model"></a> Configuring the model

Akka provides integration with several backend AI models, and you have to select which model to use. You can define a default model in `application.conf`:

src/main/resources/application.conf
```json
akka.javasdk {
  agent {
    model-provider = openai

    openai {
      model-name = "gpt-4o-mini"
    }
  }
}
```
The default model will be used if the agent doesn’t specify another model. Different agents can use different models by defining the `ModelProvider` in the Agent effect:

MyAgent.java
```java
public Effect<String> query(String question) {
  return effects()
    .model(
      ModelProvider.openAi() // (1)
        .withApiKey(System.getenv("OPENAI_API_KEY"))
        .withModelName("gpt-4o")
        .withTemperature(0.6)
        .withMaxTokens// (10000)
    )
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```

| **1** | Define the model provider in code. |

|  | With `ModelProvider.fromConfig` you can define several models in configuration and use different models in different agents. |
Available model providers for hosted models are:

| Provider | Site |
| --- | --- |
| Anthropic | [anthropic.com](https://www.anthropic.com/) |
| GoogleAIGemini | [gemini.google.com](https://gemini.google.com/) |
| Hugging Face | [huggingface.co](https://huggingface.co/) |
| OpenAi | [openai.com](https://openai.com/) |
Additionally, these model providers for locally running models are supported:

| Provider | Site |
| --- | --- |
| LocalAI | [localai.io](https://localai.io/) |
| Ollama | [ollama.com](https://ollama.com/) |
Each model provider may have different settings and those are described in [AI model provider configuration](model-provider-details.html)

It is also possible to plug in a custom model by implementing the <a href="_attachments/api/akka/javasdk/agent/ModelProvider.Custom.html">`ModelProvider.Custom`</a> interface and use it with `ModelProvider.custom`.

## <a href="about:blank#_use_componentclient_in_an_agent"></a> Use ComponentClient in an agent

[Dependency injection](setup-and-dependency-injection.html#_dependency_injection) can be used in an
Agent. For example, injecting the `ComponentClient` to be able to enrich the request to the AI model with
information from entities or views may look like this:

ActivityAgent.java
```java
@Component(id = "activity-agent")
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

  public Effect<String> query(Request request) {
    var profile = componentClient // (2)
      .forEventSourcedEntity(request.userId)
      .method(UserProfileEntity::getProfile)
      .invoke();

    var userMessage = request.message + "\nPreferences: " + profile.preferences; // (3)

    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(userMessage).thenReply();
  }
}
```

| **1** | Inject the `ComponentClient` as a constructor parameter. |
| **2** | Retrieve preferences from an entity. |
| **3** | Enrich the user message with the preferences. |
This also illustrates the important point that the context of the request to the AI model can be built from additional information in the service and doesn’t only have to come from the session memory.

The ability to reach into the rest of a distributed Akka application to *augment* requests makes behavior like Retrieval Augmented Generation (RAG) simple and less error prone than doing things manually without Akka.

<!-- <footer> -->
<!-- <nav> -->
[Components](components/index.html) [Choosing the prompt](agents/prompt.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->