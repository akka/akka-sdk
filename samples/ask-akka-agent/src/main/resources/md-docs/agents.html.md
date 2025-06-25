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

As with all other components, we supply an identifier for the component class using the `@ComponentId` annotation.

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
import akka.javasdk.annotations.ComponentId;

@ComponentId("my-agent") // (2)
public class MyAgent extends Agent { // (1)

  public Effect<String> query(String question) { // (3)
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
  }

}
```

| **1** | Create a class that extends `Agent`. |
| **2** | Make sure to annotate the class with `@ComponentId` and pass a unique identifier for this agent type. |
| **3** | Define the command handler method. |

|  | The `@ComponentId` value `my-agent` is common for all instances of this agent and must be unique across the different components in the service. |
An agent must have one command handler method that is public and returns `Effect<T>`, where `T` it the type of the reply. Alternatively it can return `StreamEffect` for [streaming responses](about:blank#_streaming_response).

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
          .model(ModelProvider // (1)
              .openAi()
              .withApiKey(System.getenv("OPENAI_API_KEY"))
              .withModelName("gpt-4o")
              .withTemperature(0.6)
              .withMaxTokens// (10000))
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
Each model provider may have different settings and those are described in [AI Model Provider Details](model-provider-details.html)

It is also possible to plug in a custom model by implementing the <a href="_attachments/api/akka/javasdk/agent/ModelProvider.Custom.html">`ModelProvider.Custom`</a> interface and use it with `ModelProvider.custom`.

## <a href="about:blank#_choosing_the_prompt"></a> Choosing the prompt

The prompt consists of essential instructions to the model.

- The system message provides system-level instructions to the AI model that defines its behavior and context. The system message acts as a foundational prompt that establishes the AI’s role, constraints, and operational parameters. It is processed before user messages and helps maintain consistent behavior throughout the interactions.
- The user message represents the specific query, instruction, or input that will be processed by the model to generate a response.
An agent that suggests real-world activities may have a prompt like:

[ActivityAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/ActivityAgent.java)
```java
@ComponentId("activity-agent")
public class ActivityAgent extends Agent {
  private static final String SYSTEM_MESSAGE = // (1)
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  public Effect<String> query(String message) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE) // (2)
        .userMessage(message)// (3)
        .thenReply();
  }
}
```

| **1** | Define the system message as a constant, but it could also be a method that adapts the system message based on the request. |
| **2** | Use the system message in the effect builder. |
| **3** | Define the user message for the specific request, and use in the effect builder. |
Keep in mind that some models have preferences in how you wrap or label user input within the system prompt and you’ll need to take that into account when defining your system message.

### <a href="about:blank#_using_dynamic_prompts_with_templates"></a> Using dynamic prompts with templates

As an alternative to hard-coded prompts, there is a built-in prompt template entity. The advantage of using the prompt template entity is that you can change the prompts at runtime without restarting or redeploying the service. Because the prompt template is managed as an entity, you retain full change history.

ActivityAgent.java
```java
@ComponentId("activity-agent")
 public class ActivityAgentWithTemplate extends Agent {
  public Effect<String> query(String message) {
   return effects()
       .systemMessageFromTemplate("activity-agent-prompt") // (1)
       .userMessage(message)//
       .thenReply();
  }
 }
```

| **1** | Define the system message prompt template key. |
In addition to the prompt template key you can optionally add parameters to `systemMessageFromTemplate`. Those will be used to format the template with `java.util.Formatter`.

Prompts are stored in the `PromptTemplate` [Event Sourced Entity](event-sourced-entities.html). This is a built-in entity, automatically registered at runtime if there are any Agent components in the service. To initialize the prompt or get the current value you can use component client the same way as for any other entity.

[ActivityPromptEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ActivityPromptEndpoint.java)
```java
@HttpEndpoint("/activity-prompts")
public class ActivityPromptEndpoint {

  private final ComponentClient componentClient;

  public ActivityPromptEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Put
  public HttpResponse update(String prompt) {
    componentClient
      .forEventSourcedEntity("activity-agent-prompt") // (1)
      .method(PromptTemplate::update) // (2)
      .invoke(prompt);

    return HttpResponses.ok();
  }

  @Get
  public String get() {
    return componentClient
      .forEventSourcedEntity("activity-agent-prompt") // (1)
      .method(PromptTemplate::get) // (3)
      .invoke();
  }
}
```

| **1** | Prompt key is used as entity id. |
| **2** | `PromptTemplate::update` update the prompt value. |
| **3** | `PromptTemplate::get` retrieves the current prompt value. |
Keeping the prompt in the Event Sourced Entity lets you see the history of all changes. It’s also possible to subscribe to changes in the prompt template entity, so that you can build a [View](views.html) or react to changes in the prompt.

The following table describes all of the methods available for the `PromptTemplate` entity:

| Method | Description |
| --- | --- |
| `init` | Initializes the prompt template with a given value. If the prompt template already exists, it will not change it. Useful for setting the initial value, e.g. in the `onStartup` method of the [ServiceSetup](setup-and-dependency-injection.html#_service_lifecycle). |
| `update` | Updates the prompt template with a new value. If the prompt template does not exist, it will create it. If the value is the same as the current value, it will not change it. |
| `get` | Retrieves the current value of the prompt template. If the prompt template does not exist, it will throw an exception. |
| `getOptional` | Retrieves the current value of the prompt template as an `Optional`. If the prompt template does not exist, it will return an empty `Optional`. |
| `delete` | Deletes the prompt template. |
Although the system message has a dedicated method to use the prompt template, you can also use it for the user message. In that case you have to use the component client to retrieve the current value of the prompt template and pass it as the user message.

### <a href="about:blank#_adding_more_context"></a> Adding more context

[Retrieval-Augmented Generation (RAG)](rag.html) is a technique to provide additional, relevant content in the user message.

## <a href="about:blank#_calling_the_agent"></a> Calling the Agent

Use the `ComponentClient` to call the agent from a Workflow, Endpoint or Consumer.

```java
var sessionId = UUID.randomUUID().toString();
    String suggestion =
        componentClient
            .forAgent()// (1)
            .inSession(sessionId)// (2)
            .method(ActivityAgent::query)
            .invoke("Business colleagues meeting in London");
```

| **1** | Use `forAgent`. |
| **2** | Define the identifier of the session that the agent participates in. |
The session id is used by the [session memory](about:blank#session_memory), but it is also important for observability tracking and AI evaluation.

You can use a new random UUID for each call if the agent doesn’t collaborate with other agents nor have a multi-step interaction with the AI model. Deciding how you manage sessions will be an important part of designing the agentic parts of your application.

For more details about the `ComponentClient`, see [Component and service calls](component-and-service-calls.html).

## <a href="about:blank#_drive_the_agent_from_a_workflow"></a> Drive the agent from a workflow

Agents make external calls to the AI model and possibly other services, and therefore it is important to have solid error handling and durable execution steps when calling agents. In many cases it is a good recommendation to call agents from a [Workflow](workflows.html). The workflow will automatically execute the steps in a reliable and durable way. This means that if a call in a step fails, it will be retried until it succeeds or the retry limit of the recovery strategy is reached and separate error handling can be performed. The state machine of the workflow is durable, which means that if the workflow is restarted for some reason it will continue from where it left off, i.e. execute the current non-completed step again.

A workflow will typically orchestrate several agents, which collaborate in achieving a common goal. Even if you only have a single agent, having a workflow manage retries, failures, and timeouts can be invaluable.

We will look more at [multi-agent systems](about:blank#multi_agent), but let’s start with a workflow for the single activities agent.

[ActivityAgentManager.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/ActivityAgentManager.java)
```java
@ComponentId("activity-agent-manager")
public class ActivityAgentManager extends Workflow<ActivityAgentManager.State> { // (1)

  public record State(String userQuery, String answer) { // (2)
    State withAnswer(String a) {
      return new State(userQuery, a);
    }
  }

  private final ComponentClient componentClient;

  public ActivityAgentManager(ComponentClient componentClient) { // (3)
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) { // (4)
    return effects()
        .updateState(new State(query, ""))
        .transitionTo("activities")
        .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<String> getAnswer() { // (5)
    if (currentState() == null || currentState().answer.isEmpty()) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowDef<State> definition() { // (6)
    return workflow()
        .addStep(suggestActivities())
        .addStep(error())
        .defaultStepRecoverStrategy(maxRetries// (2).failoverTo("error"));
  }

  private Step suggestActivities() { // (7)
    return step("activities")
        .call(() ->
            componentClient
              .forAgent()
              .inSession(sessionId())
              .method(ActivityAgent::query)// (8)
              .invoke(currentState().userQuery))
        .andThen(String.class, suggestion -> {
          logger.info("Activities: {}", suggestion);

          return effects()
              .updateState(currentState().withAnswer(suggestion)) // (9)
              .end();
        })
        .timeout(Duration.ofSeconds// (60));
  }

  private Step error() {
    return step("error")
        .call(Done::getInstance)
        .andThen(() -> effects().end());
  }

  private String sessionId() { // (10)
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
```

| **1** | Extend `Workflow`. |
| **2** | The state can hold intermediate and final results, and it is durable. |
| **3** | Inject the `ComponentClient`, which will be used when calling the agent. |
| **4** | This workflow only has two command handler methods. One that starts the workflow with the initial user request, |
| **5** | and one to retrieve the final answer. |
| **6** | Define the steps of the workflow. |
| **7** | The step that calls the `ActivityAgent` |
| **8** | Call the agent with the `ComponentClient` |
| **9** | Store the result from the agent. |
| **10** | The workflow corresponds to an agent session. |
The workflow itself will be instantiated by making a call to the `start` method from an endpoint or a consumer.

Keep in mind that AI requests are typically slow (many seconds), and you need to define the workflow timeouts accordingly. This is specified in the workflow step definition with:

```java
.timeout(Duration.ofSeconds// (60))
```
Additionally, you should define a workflow recovery strategy so that it doesn’t retry failing requests infinitely. This is specified in the workflow definition with:

```java
.defaultStepRecoverStrategy(maxRetries// (2).failoverTo("error"))
```
More details in [Workflow timeouts and recovery strategy](workflows.html#_error_handling).

### <a href="about:blank#_human_in_the_loop"></a> Human in the loop

You often need a human-in-the-loop to integrate human oversight into the AI’s decision-making process. A workflow can be paused, waiting for user input. When the approval command is received, the workflow can continue from where it left off and transition to the next step in the agentic process.

See [how to pause a workflow](workflows.html#_pausing_workflow).

## <a href="about:blank#session_memory"></a> Managing session memory

Session Memory provides a history mechanism that enables agents to maintain context across multiple interactions. This feature is essential for building agents that can remember previous exchanges with users, understand context, and provide coherent responses over time.

When an agent interacts with an AI model, both the user message and the AI response are automatically stored in the session memory. These messages are then included as additional context in subsequent requests to the model, allowing it to reference previous parts of the interaction.

The session memory is:

- Identified by a session ID that links related interactions
- Shared between multiple agents if they use the same session ID
- Persisted as an event-sourced entity
- Automatically managed by the Agent

### <a href="about:blank#_session_memory_configuration"></a> Session memory configuration

By default, session memory is enabled for all agents. You can configure it globally in your `application.conf`:

```conf
akka.javasdk.agent.memory {
  enabled = true
  limited-window {
    max-size = 156KiB # max history size before oldest message start being removed
  }
}
```
Or you can configure memory behavior for specific agent interactions using the `MemoryProvider` API.

Example with `limitedWindow` memory provider:

```java
public Effect<String> ask(String question) {
      return effects()
          .memory(MemoryProvider.limitedWindow().readLast// (5))
          .systemMessage("You are a helpful...")
          .userMessage(question)
          .thenReply();
    }
```
Example disabling session memory for the agent:

```java
public Effect<String> ask(String question) {
      return effects()
          .memory(MemoryProvider.none())
          .systemMessage("You are a helpful...")
          .userMessage(question)
          .thenReply();
    }
```

### <a href="about:blank#_different_memory_providers"></a> Different memory providers

The <a href="_attachments/api/akka/javasdk/agent/MemoryProvider.html">`MemoryProvider`</a> interface allows you to control how session memory behaves:

- `MemoryProvider.none()` - Disables both reading from and writing to session memory
- `MemoryProvider.limitedWindow()` - Configures memory with options to, e.g.:

  - Setup **read only** memory, in which the agent reads the memory but does not allow write any interactions to it. This is ideal for multi-agent sessions where some agents can store memory and others can’t.
  - Setup **write only** memory, in which the agent register the interactions to the session memory but does not take those in consideration when processing the user message.
  - Limit the amount of messages used as context in each interaction, i.e. use only the last N number of messages for context (good for token usage control).
- `MemoryProvider.custom()` - Allows you to provide a custom implementation for the `SessionMemory` interface and store the session memory externally in a database / service of your preference.

### <a href="about:blank#_accessing_session_memory"></a> Accessing session memory

The default implementation of Session Memory is backed by a regular [Event Sourced Entity](event-sourced-entities.html) called `SessionMemoryEntity`, which allows you to interact directly with it as you would do with any other entities in your application. This includes the possibility to directly modify or access it through the `ComponentClient` but also the ability to subscribe to changes in the session memory, as shown below:

[SessionMemoryConsumer.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/SessionMemoryConsumer.java)
```java
@ComponentId("session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(getClass());


  public Effect onSessionMemoryEvent(SessionMemoryEntity.Event event) {
    var sessionId = messageContext().eventSubject().get();

    switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded userMsg ->
        logger.info("User message added to session {}: {}",
            sessionId, userMsg.message());
      // ...

      default -> logger.debug("Unhandled session memory event: {}", event);
    }

    return effects().done();
  }
}
```
This can be useful for more granular control over token usage but also to allow external integrations and analytics over these details.

### <a href="about:blank#_compaction"></a> Compaction

You can update the session memory to reduce the size of the history. One technique is to let an LLM summarize the interaction history and use the new summary instead of the full history. Such agent can look like this:

[CompactionAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/CompactionAgent.java)
```java
@ComponentId("compaction-agent")
public class CompactionAgent extends Agent {
  private static final String SYSTEM_MESSAGE =
      """
      You can compact an interaction history with an LLM. From the given
      USER, TOOL_CALL_RESPONSE and AI messages you create one single user message and one single
      ai message.
      
      The interaction history starts with USER: followed by the user message.
      For each user message there is a corresponding response for AI that starts with AI:
      Keep the original style of user question and AI answer in the summary.
      
      Note that AI messages may contain TOOL_CALL_REQUEST(S) and be followed by TOOL_CALL_RESPONSE(S).
      Make sure to keep this information in the generated ai message.
      Do not keep it as structured tool calls, but make sure to extract the relevant context.
      
      Your response should follow a strict json schema as defined bellow.
      {
        "userMessage": "<the user message summary>",
        "aiMessage: "<the AI message summary>",
      }
      
      Do not include any explanations or text outside of the JSON structure.
      """.stripIndent(); // (1)

  public record Result(String userMessage, String aiMessage) {
  }

  private final ComponentClient componentClient;

  public CompactionAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Result> summarizeSessionHistory(SessionHistory history) { // (2)
    String concatenatedMessages =
        history.messages().stream().map(msg -> {
              return switch (msg) {
                case SessionMessage.UserMessage userMsg -> "\n\nUSER:\n" + userMsg.text(); // (3)

                case SessionMessage.AiMessage aiMessage -> {
                  var aiText = "\n\nAI:\n" + aiMessage.text();
                  yield aiMessage.toolCallRequests()
                    .stream()
                    .reduce(
                      aiText,
                      // if there are tool requests, also append them to the aiText
                      (acc, req) -> acc +
                        "\n\tTOOL_CALL_REQUEST: id=" + req.id() + ", name=" + req.name() + ", args=" + req.arguments() + " \n",
                      String::concat);
                }

                case SessionMessage.ToolCallResponse toolRes -> "\n\nTOOL_CALL_RESPONSE:\n" + toolRes.text();
              };
            })
            .collect(Collectors.joining()); // (3)

    return effects()
        .memory(MemoryProvider.none()) // (4)
        .model(ModelProvider
            .openAi()
            .withModelName("gpt-4o-mini")
            .withApiKey(System.getenv("OPENAI_API_KEY"))
            .withMaxTokens// (1000))
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(concatenatedMessages)
        .responseAs(Result.class)

      .thenReply();
  }
}
```

| **1** | Instructions to create the summary of user and AI messages and result as JSON. |
| **2** | The full history from the `SessionMemoryEntity`. |
| **3** | Format and concatenate the messages. |
| **4** | The `CompactionAgent` itself doesn’t need any session memory. |
One way to trigger compaction is to use a consumer of the session memory events and call the `CompactionAgent` from that consumer when a threshold is exceeded.

[SessionMemoryConsumer.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/SessionMemoryConsumer.java)
```java
@ComponentId("session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ComponentClient componentClient;

  public SessionMemoryConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onSessionMemoryEvent(SessionMemoryEntity.Event event) {
    var sessionId = messageContext().eventSubject().get();

    switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded userMsg ->
        logger.info("User message added to session {}: {}",
            sessionId, userMsg.message());
      // ...
      case SessionMemoryEntity.Event.AiMessageAdded aiMsg -> {
        if (aiMsg.historySizeInBytes() > 100000) { // (1)
          var history = componentClient
              .forEventSourcedEntity(sessionId)
              .method(SessionMemoryEntity::getHistory) // (2)
              .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.empty()));

          var summary =
              componentClient.forAgent().inSession(sessionId)
                  .method(CompactionAgent::summarizeSessionHistory) // (3)
                  .invoke(history);

          var now = Instant.now();
          componentClient
              .forEventSourcedEntity(sessionId)
              .method(SessionMemoryEntity::compactHistory) // (4)
              .invoke(new SessionMemoryEntity.CompactionCmd(
                  new SessionMessage.UserMessage(now, summary.userMessage(), ""),
                  new SessionMessage.AiMessage(now, summary.aiMessage(), ""),
                  history.sequenceNumber() // (5)
              ));
        }
      }


      default -> logger.debug("Unhandled session memory event: {}", event);
    }

    return effects().done();
  }
}
```

| **1** | The AiMessageAdded has the total size of the history. |
| **2** | Retrieve the full history from the `SessionMemoryEntity`. |
| **3** | Call the agent to make the summary. |
| **4** | Store the summary as the new compacted history in the `SessionMemoryEntity`. |
| **5** | To support concurrent updates, the `sequenceNumber` of the retrieved history is included in the `CompactionCmd`. |

## <a href="about:blank#_structured_responses"></a> Structured responses

Many LLMs support generating outputs in a structured format, typically JSON. You can easily map such output to Java objects using the effect API.

```java
@ComponentId("activity-agent")
 public class ActivityAgentStructuredResponse extends Agent {

  private static final String SYSTEM_MESSAGE = // (1)
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      
      Your response should be a JSON object with the following structure:
      {
        "name": "Name of the activity",
        "description": "Description of the activity"
      }
      
      Do not include any explanations or text outside of the JSON structure.
      """.stripIndent();

  private static final Activity DEFAULT_ACTIVITY =
      new Activity("running", "Running is a great way to stay fit " +
          "and healthy. You can do it anywhere, anytime, and it requires no special equipment.");

  record Activity(String name, String description) {} // (2)

  public Effect<Activity> query(String message) {
   return effects()
       .systemMessage(SYSTEM_MESSAGE)
       .userMessage(message)
       .responseAs(Activity.class) // (3)
       .onFailure(throwable -> { // (4)
        if (throwable instanceof JsonParsingException jsonParsingException) {
         return DEFAULT_ACTIVITY;
        } else {
         throw new RuntimeException(throwable);
        }
       })
       .thenReply();
  }
 }
```

| **1** | Instruct the model to return a structured response in JSON format. |
| **2** | `Activity` record is used to map the JSON response to a Java object. |
| **3** | Use the `responseAs` method to specify the expected response type. |
| **4** | Sometimes the model may not return a valid JSON, so you can use `onFailure` to provide a fallback value in case of parsing exception. |

## <a href="about:blank#tools"></a> Extending agents with function tools

You may frequently hear people say things like "the LLM can make a call" or "the LLM can use a tool". While these statements get the point across, they’re not entirely accurate. In truth, the agent will tell the LLM which *tools* are available for use. The LLM then determines from the prompt which tools it needs to call and with which parameters.

The Agent will then in turn execute the tool requested by the LLM, incorporate the tool results into the session context, and then send a new prompt. This will continue in a loop until the LLM no longer indicates it needs to invoke a tool to perform its task.

There are three ways to add function tools to your agent:

1. **Agent-defined function tools** — Define function tools directly within your agent class using the `@FunctionTool` annotation. These are automatically registered as available tools for the current Agent.
2. **Externally defined function tools** — Explicitly register external objects or classes containing function tools by
passing them to the `effects().tools()` method in your agent’s command handler. Objects or classes passed to `effects
().tools()` must have at least one public method annotated with `@FunctionTool`.
3. **Tools defined by remote MCP servers** – Register remote MCP servers to let the agent use tools they provide.

|  | A class (either the agent itself or an external tool class) can have multiple methods annotated with `@FunctionTool`. Each annotated method will be registered as a separate tool that the LLM can choose to invoke based on the task requirements. |
You can use either approach independently or combine them based on your needs. Let’s look at a complete example showing both approaches:

[WeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherAgent.java)
```java
public class WeatherAgent extends Agent {
  private final WeatherService weatherService;

  public WeatherAgent(WeatherService weatherService) {
    this.weatherService = weatherService; // (1)
  }

  public Effect<String> query(AgentRequest request) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .tools(weatherService) // (2)
        .userMessage(request.message())
        .thenReply();
  }

  @FunctionTool(description = "Return current date in yyyy-MM-dd format") // (3)
  private String getCurrentDate() {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }
}
```

| **1** | The `WeatherService` providing a function tool is injected into the agent (see [DependencyProvider](setup-and-dependency-injection.html#_custom_dependency_injection)). |
| **2** | We explicitly register the `weatherService` using the `tools()` method to make its method available as a tool for
the current Agent. |
| **3** | We define a simple tool directly in the agent class using the `@FunctionTool` annotation, which is implicitly registered. Note that since this method is defined in the agent itself, it can even be a private method. |
The `WeatherService` is an interface with a method annotated with `@FunctionTool`. A concrete implementation of this
interface is provided by `WeatherServiceImpl` class.
This class is made available for injection in the service setup using a [DependencyProvider](setup-and-dependency-injection.html#_custom_dependency_injection).

[WeatherService.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherService.java)
```java
public interface WeatherService {

  @FunctionTool(description = "Returns the weather forecast for a given city.") // (1)
  String getWeather(
    @Description("A location or city name.")
    String location, // (2)
    @Description("Forecast for a given date, in yyyy-MM-dd format.")
    Optional<String> date); // (3)
}
```

| **1** | Annotate method with `@FunctionTool` and provide a clear description of what it does. |
| **2** | Parameters can be documented with the `@Description` annotation to help the LLM understand how to use them. |
| **3** | The date parameter is optional. The LLM may call `getCurrentDate` first or call this method without a date, depending on the user query. |

|  | LLMs are all about context. The more context you can provide, the better the results.
Both `@FunctionTool` and `@Description` annotations are used to provide context to the LLM about the tool function and its parameters.
The better the context, the better the LLM can understand what the tool function does and how to use it. |
In this example, the agent has access to both:

- The `getCurrentDate()` method defined within the agent class (implicitly registered via annotation)
- The `getWeather()` method defined in the `WeatherService` interface (explicitly registered via the `.tools()` method)

### <a href="about:blank#_sharing_function_tools_across_agents"></a> Sharing function tools across agents

Function tools defined in external classes can be shared and reused across multiple agents. This approach promotes code reusability and helps maintain a consistent behavior for common functionalities.

When a tool like `WeatherService` is shared across multiple agents:

- Each agent can register the same tool but use it in different contexts
- The tool behavior remains consistent, but how and when agents invoke it may differ based on their specific tasks
- Agents provide different system prompts that influence how the LLM decides to use the shared tool

### <a href="about:blank#_lazy_initialization_of_tool_classes"></a> Lazy initialization of tool classes

In the example above, we pass an instance of `WeatherService` to the `tools()` method. Alternatively, you can pass the `Class` object instead:

java]
```java
public Effect<AgentResponse> query(String message) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .tools(WeatherService.class) // (1)
        .userMessage(message)
        .responseAs(AgentResponse.class)
        .thenReply();
  }
```

| **1** | The WeatherService is passed as a `Class` instead of an instance. It will be instantiated when the agent needs to use it. |
When you pass a `Class` instead of an instance, the class is only instantiated when the agent actually needs to use the tool.

For this approach to work, you must register the class with a [DependencyProvider](setup-and-dependency-injection.html#_custom_dependency_injection) in your service setup. The DependencyProvider is responsible for creating and managing instances of these classes when they’re needed. This gives you complete control over how tool dependencies are instantiated and managed throughout your application.

### <a href="about:blank#_using_tools_from_remote_mcp_servers"></a> Using tools from remote MCP servers

[Akka MCP endpoints](mcp-endpoints.html) declared in other services, or third party MCP services can be added to
the agent. By default, all tools provided by each added remote MCP server are included, but it is possible to filter
available tools from each server based on their name.

It is also possible to intercept, modify, or deny MCP tool requests, or their responses by defining a `RemoteMcpTools.ToolInterceptor`.

[RemoteMcpWeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/RemoteMcpWeatherAgent.java)
```java
public Effect<AgentResponse> query(String message) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .mcpTools(
            RemoteMcpTools.fromService("weather-service"), // (1)
            RemoteMcpTools.fromServer("https://weather.example.com/mcp") // (2)
                .addClientHeader(Authorization.oauth2(System.getenv("WEATHER_API_TOKEN"))) // (3)
                .withAllowedToolNames(Set.of("get_weather")) // (4)
        )
        .userMessage(message)
        .responseAs(AgentResponse.class)
        .thenReply();
  }
```

| **1** | For MCP endpoints in other Akka services, use HTTP and the deployed service name |
| **2** | For third party MCP servers use the fully qualified host name and make sure to use HTTPS as the requests will
go over the public internet. |
| **3** | Custom headers to pass along can be defined |
| **4** | As well as filters of what tools to allow. |
When using MCP endpoints in other Akka services, the service ACLs apply just like for [HTTP endpoints](http-endpoints.html) and [gRPC endpoints](grpc-endpoints.html).

### <a href="about:blank#configuring_tool_call_limits"></a> Configuring tool call limits

Inside a single request/response cycle, an LLM can successively request the agent to call functions tools or MCP tools. After analyzing the result of a call, the LLM might decide to request another call to gather more context. The `akka.javasdk.agent.max-tool-call-steps` setting limits how many such steps may occur between a user request and the final AI response.

By default, this value is set to 100. You can adjust this in your configuration:

application.conf
```hocon
# Increase the limit to allow more tool calls
akka.javasdk.agent.max-tool-call-steps = 150
```

## <a href="about:blank#_use_componentclient_in_an_agent"></a> Use ComponentClient in an agent

[Dependency injection](setup-and-dependency-injection.html#_dependency_injection) can be used in an
Agent. For example, injecting the `ComponentClient` to be able to enrich the request to the AI model with
information from entities or views may look like this:

ActivityAgent.java
```java
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

  public Effect<String> query(Request request) {
   var profile = componentClient // (2)
       .forEventSourcedEntity(request.userId)
       .method(UserProfileEntity::getProfile)
       .invoke();

   var userMessage = request.message + "\nPreferences: " + profile.preferences; // (3)

   return effects()
       .systemMessage(SYSTEM_MESSAGE)
       .userMessage(userMessage)
       .thenReply();
  }
 }
```

| **1** | Inject the `ComponentClient` as a constructor parameter. |
| **2** | Retrieve preferences from an entity. |
| **3** | Enrich the user message with the preferences. |
This also illustrates the important point that the context of the request to the AI model can be built from additional information in the service and doesn’t only have to come from the session memory.

The ability to reach into the rest of a distributed Akka application to *augment* requests makes behavior like Retrieval Augmented Generation (RAG) simple and less error prone than doing things manually without Akka.

## <a href="about:blank#_streaming_responses"></a> Streaming responses

In AI chat applications, you’ve seen how responses are displayed word by word as they are generated. There are a few reasons for this. The first is that LLMs are *prediction* engines. Each time a token (usually a word) is streamed to the response, the LLM will attempt to *predict* the next word in the output. This causes the small delays between words.

The other reason why responses are streamed is that it can take a very long time to generate the full response, so the user experience is much better getting the answer as a live stream of tokens. To support this real-time user experience, the agent can stream the model response tokens to an endpoint. These tokens can then be pushed to the client using server-sent events (SSE).

```java
@ComponentId("streaming-activity-agent")
 public class StreamingActivityAgent extends Agent {
  private static final String SYSTEM_MESSAGE =
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  public StreamEffect query(String message) { // (1)
   return streamEffects() // (2)
       .systemMessage(SYSTEM_MESSAGE)
       .userMessage(message)
       .thenReply();
  }
 }
```

| **1** | The method returns `StreamEffect` instead of `Effect<T>`. |
| **2** | Use the `streamEffects()` builder. |
Consuming the stream from an HTTP endpoint:

```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
 @HttpEndpoint("/api")
 public class ActivityHttpEndpoint {

  public record Request(String sessionId, String question) {
  }

  private final ComponentClient componentClient;

  public ActivityHttpEndpoint(ComponentClient componentClient) {
   this.componentClient = componentClient;
  }

  @Post("/ask")
  public HttpResponse ask(Request request) {
   var responseStream = componentClient
       .forAgent()
       .inSession(request.sessionId)
       .tokenStream(StreamingActivityAgent::query) // (1)
       .source(request.question); // (2)

   return HttpResponses.serverSentEvents(responseStream); // (3)
  }


 }
```

| **1** | Use `tokenStream` of the component client, instead of `method`, |
| **2** | and invoke it with `source` to receive a stream of tokens. |
| **3** | Return the stream of tokens as SSE. |
The returned stream is a `Source<String, NotUsed>`, i.e. the tokens are always text strings.

The granularity of a token varies by AI model, often representing a word or a short sequence of characters. To reduce the overhead of sending each token as a separate SSE, you can group multiple tokens together using the Akka streams `groupWithin` operator.

```java
@Post("/ask-grouped")
  public HttpResponse askGrouped(Request request) {
   var tokenStream = componentClient
       .forAgent()
       .inSession(request.sessionId)
       .tokenStream(StreamingActivityAgent::query)
       .source(request.question);

   var groupedTokenStream =
       tokenStream
           .groupedWithin(20, Duration.ofMillis// (100)) // (1)
           .map(group -> String.join("", group)); // (2)

   return HttpResponses.serverSentEvents(groupedTokenStream); // (3)
  }
```

| **1** | Group at most 20 tokens or within 100 milliseconds, whatever happens first. |
| **2** | Concatenate the list of string into a single string. |
| **3** | Return the stream of grouped tokens as SSE. |

|  | Token streams are designed for direct agent calls from an endpoint. You can’t use a token stream when you have an intermediate workflow between the endpoint and the agent. |

## <a href="about:blank#multi_agent"></a> Orchestrating multiple agents

A single agent performs one well-defined task. Several agents can collaborate to achieve a common goal. The agents should be orchestrated from a predefined workflow or a dynamically created plan.

### <a href="about:blank#_using_a_predefined_workflow"></a> Using a predefined workflow

Let’s first look at how to define a workflow that orchestrates several agents in a predefined steps. This is similar to the <a href="about:blank#_drive_the_agent_from_a_workflow">`ActivityAgentManager`</a> that was illustrated above, but it uses both the `WeatherAgent` and the `ActivityAgent`. First it retrieves the weather forecast and then it finds suitable activities.

```java
@ComponentId("agent-team")
public class AgentTeam extends Workflow<AgentTeam.State> {
  private static final Logger logger = LoggerFactory.getLogger(AgentTeam.class);

  public record State(String userQuery, String weatherForecast, String answer) {
    State withWeatherForecast(String f) {
      return new State(userQuery, f, answer);
    }

    State withAnswer(String a) {
      return new State(userQuery, weatherForecast, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeam(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) {
    return effects()
        .updateState(new State(query, "", ""))
        .transitionTo("weather") // (1)
        .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().answer.isEmpty()) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowDef<State> definition() {
    return workflow()
        .addStep(askWeather())
        .addStep(suggestActivities())
        .addStep(error())
        .defaultStepRecoverStrategy(maxRetries// (2).failoverTo("error"));
  }

  private Step askWeather() { // (2)
    return step("weather")
        .call(() ->
            componentClient
                .forAgent()
                .inSession(sessionId())
                .method(WeatherAgent::query)
                .invoke(currentState().userQuery))
        .andThen(String.class, forecast -> {
          logger.info("Weather forecast: {}", forecast);

          return effects()
              .updateState(currentState().withWeatherForecast(forecast))// (3)
              .transitionTo("activities");
        })
        .timeout(Duration.ofSeconds// (60));
  }

  private Step suggestActivities() {
    return step("activities")
        .call(() -> {
          String request = currentState().userQuery +
              "\nWeather forecast: " + currentState().weatherForecast; // (4)
          return componentClient
              .forAgent()
              .inSession(sessionId())
              .method(ActivityAgent::query)
              .invoke(request);
        })
        .andThen(String.class, suggestion -> {
          logger.info("Activities: {}", suggestion);

          return effects()
              .updateState(currentState().withAnswer(suggestion)) // (5)
              .end();
        })
        .timeout(Duration.ofSeconds// (60));
  }

  private Step error() {
    return step("error")
        .call(() -> null)
        .andThen(() -> effects().end());
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
```

| **1** | The workflow starts by asking for the weather forecast. |
| **2** | Weather forecast is retrieved by the `WeatherAgent`, which must extract the location and date from the user query. |
| **3** | The forecast is stored in the state of the workflow. |
| **4** | The forecast is included in the request to the `ActivityAgent`. |
| **5** | The final result is stored in the workflow state. |
In
![steps 4](../concepts/_images/steps-4.svg)
we explicitly include the forecast in the request to the `ActivityAgent`. That is not strictly necessary because the agents share the same session memory and thereby the `ActivityAgent` will already have the weather forecast in the context that is sent to the AI model.

The workflow will automatically execute the steps in a reliable and durable way. This means that if a call in a step fails, it will be retried until it succeeds or the retry limit of the recovery strategy is reached and separate error handling can be performed. The state machine of the workflow is durable, which means that if the workflow is restarted for some reason it will continue from where it left off, i.e. execute the current non-completed step again.

### <a href="about:blank#_creating_dynamic_plans"></a> Creating dynamic plans

To create a more flexible and autonomous agentic system you want to analyze the problem and dynamically come up with a plan. The agentic system should identify the tasks to achieve the goal by itself. Decide which agents to use and in which order to execute them. Coordinate input and output between agents and adjust the plan along the way.

There are several approaches for the planning, such as using deterministic algorithms or using AI also for the planning. We will look at how we can use AI for analyzing a request, selecting agents and in which order to use them.

We split the planning into two steps and use two separate agents for these tasks. It’s not always necessary to use several steps for the planning. You have to experiment with what works best for your problem domain.

1. Select agents that are useful for a certain problem.
2. Decide in which order to use the agents and give each agent precise instructions for its task.
The `SelectorAgent` decides which agents to use:

[SelectorAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/SelectorAgent.java)
```java
@ComponentId("selector-agent")
@AgentDescription(
    name = "Selector Agent",
    description = """
      An agent that analyses the user request and selects useful agents for
      answering the request.
    """
)
public class SelectorAgent extends Agent {

  private final String systemMessage;

  public SelectorAgent(AgentRegistry agentsRegistry) { // (1)

    var agents = agentsRegistry.agentsWithRole("worker"); // (2)

    this.systemMessage = """
        Your job is to analyse the user request and select the agents that should be used to answer
        the user. In order to do that, you will receive a list of available agents. Each agent has
        an id, a name and a description of its capabilities.
      
        For example, a user may be asking to book a trip. If you see that there is a weather agent,
        a city trip agent and a hotel booking agent, you should select those agents to complete the
        task. Note that this is just an example. The list of available agents may vary, so you need
        to use reasoning to dissect the original user request and using the list of available agents,
        decide which agents must be selected.
      
        You don't need to come up with an execution order. Your task is to analyze user's request and
        select the agents.
      
        Your response should follow a strict json schema as defined bellow.
        It should contain a single field 'agents'. The field agents must be array of strings
        containing the agent's IDs. If none of the existing agents are suitable for executing
        the task, you return an empty array.
      
         {
           "agents": []
         }
      
        Do not include any explanations or text outside of the JSON structure.
      
        You can find the list of existing agents below (in JSON format):
        Also important, use the agent id to identify the agents.
        %s
      """
        .stripIndent()
        .formatted(JsonSupport.encodeToString(agents)); // (3)
  }


  public Effect<AgentSelection> selectAgents(String message) {
    return effects()
        .systemMessage(systemMessage)
        .userMessage(message)
        .responseAs(AgentSelection.class)
        .thenReply();
  }
}
```

| **1** | The `AgentRegistry` contains information about all agents. |
| **2** | Select the agents with the role `"worker"`. |
| **3** | Detailed instructions and include descriptions (as json) of the agents. |
The information about the agents in the `AgentRegistry` comes from the `@ComponentId` and `@AgentDescription` annotations. When using it for planning like this it is important that the agents define those descriptions that the LLM can use to come up with a good plan.

The `WeatherAgent` has:

[WeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherAgent.java)
```java
@ComponentId("weather-agent")
@AgentDescription(
    name = "Weather Agent",
    description = """
      An agent that provides weather information. It can provide current weather, forecasts, and other
      related information.
    """,
    role = "worker"
)
public class WeatherAgent extends Agent {
```
The `ActivityAgent` has:

[ActivityAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/ActivityAgent.java)
```java
@ComponentId("activity-agent")
@AgentDescription(
  name = "Activity Agent",
  description = """
      An agent that suggests activities in the real world. Like for example,
      a team building activity, sports, an indoor or outdoor game, board games, a city trip, etc.
    """,
    role = "worker"
)
public class ActivityAgent extends Agent {
```
Note that in
![steps 2](../concepts/_images/steps-2.svg)
of the `Selector` we retrieve a subset of the agents with a certain role. This role is also defined in the `@AgentDescription` annotation.

The result from the `Selector` is a list of agent ids:

[AgentSelection.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/domain/AgentSelection.java)
```java
public record AgentSelection(List<String> agents) {
}
```
After selecting agents, we use a `PlannerAgent` to decide in which order to use the agents and what precise request that each agent should receive to perform its single task.

[PlannerAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/PlannerAgent.java)
```java
@ComponentId("planner-agent")
@AgentDescription(
    name = "Planner",
    description = """
        An agent that analyzes the user request and available agents to plan the tasks
        to produce a suitable answer.
        """)
public class PlannerAgent extends Agent {

  public record Request(String message, AgentSelection agentSelection) {}

  private final AgentRegistry agentsRegistry;

  public PlannerAgent(AgentRegistry agentsRegistry) {
    this.agentsRegistry = agentsRegistry;
  }

  private String buildSystemMessage(AgentSelection agentSelection) {
    var agents = agentSelection.agents().stream().map(agentsRegistry::agentInfo).toList(); // (1)
    return """
        Your job is to analyse the user request and the list of agents and devise the best order in
        which the agents should be called in order to produce a suitable answer to the user.
      
        You can find the list of exiting agents below (in JSON format):
        %s
      
        Note that each agent has a description of its capabilities. Given the user request,
        you must define the right ordering.
      
        Moreover, you must generate a concise request to be sent to each agent. This agent request is
        of course based on the user original request, but is tailored to the specific agent. Each
        individual agent should not receive requests or any text that is not related with its domain
        of expertise.
      
        Your response should follow a strict json schema as defined bellow.
         {
           "steps": [
              {
                "agentId": "<the id of the agent>",
                "query: "<agent tailored query>",
              }
           ]
         }
      
        The '<the id of the agent>' should be filled with the agent id.
        The '<agent tailored query>' should contain the agent tailored message.
        The order of the items inside the "steps" array should be the order of execution.
      
        Do not include any explanations or text outside of the JSON structure.
      
      """.stripIndent()
      // note: here we are not using the full list of agents, but a pre-selection
      .formatted(JsonSupport.encodeToString(agents)); // (2)
  }

  public Effect<Plan> createPlan(Request request) {
    if (request.agentSelection.agents().size() == 1) {
      // no need to call an LLM to make a plan where selection has a single agent
      var step = new PlanStep(request.agentSelection.agents().getFirst(), request.message());
      return effects().reply(new Plan(List.of(step)));
    } else {
      return effects()
        .systemMessage(buildSystemMessage(request.agentSelection))
        .userMessage(request.message())
        .responseAs(Plan.class)
        .thenReply();
      }
  }
}
```

| **1** | Lookup the agent information for the selected agents from the `AgentRegistry. |
| **2** | Detailed instructions and include descriptions (as json) of the agents. |
That’s the two agents that perform the planning, but we also need to connect them and execute the plan. This orchestration is the job of a workflow, called `AgentTeam`.

[AgentTeam.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/AgentTeam.java)
```java
@ComponentId("agent-team")
public class AgentTeam extends Workflow<AgentTeam.State> { // (1)
  public record Request(String userId, String message) {}

  @Override
  public WorkflowDef<State> definition() {
    return workflow()
      .defaultStepRecoverStrategy(maxRetries// (1).failoverTo(INTERRUPT))
      .defaultStepTimeout(Duration.of(30, SECONDS))
      .addStep(selectAgentsStep()) // (2)
      .addStep(planStep())
      .addStep(executePlanStep())
      .addStep(summarizeStep())
      .addStep(interruptStep());
  }

  public Effect<Done> start(Request request) {
    if (currentState() == null) {
      return effects()
        .updateState(State.init(request.userId(), request.message()))
        .transitionTo(SELECT_AGENTS) // (3)
        .thenReply(Done.getInstance());
    } else {
      return effects().error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }
  private static final String SELECT_AGENTS = "select-agents";

  private Step selectAgentsStep() {
    return step(SELECT_AGENTS)
      .call(() ->
          componentClient.forAgent().inSession(sessionId()).method(SelectorAgent::selectAgents)
              .invoke(currentState().userQuery)) // (4)
      .andThen(AgentSelection.class, selection -> {
        logger.debug("Selected agents: {}", selection.agents());
          if (selection.agents().isEmpty()) {
            var newState = currentState()
              .withFinalAnswer("Couldn't find any agent(s) able to respond to the original query.")
              .failed();
            return effects().updateState(newState).end(); // terminate workflow
          } else {
            return effects().transitionTo(CREATE_PLAN, selection); // (5)

          }
        }
      );
  }

  private static final String CREATE_PLAN = "create-plan";

  private Step planStep() {
    return step(CREATE_PLAN)
      .call(AgentSelection.class, agentSelection -> {
        logger.debug(
            "Calling planner with: '{}' / {}",
            currentState().userQuery,
            agentSelection.agents());

          return componentClient.forAgent().inSession(sessionId()).method(PlannerAgent::createPlan)
              .invoke(new PlannerAgent.Request(currentState().userQuery, agentSelection)); // (6)
        }
      )
      .andThen(Plan.class, plan -> {
        logger.debug("Execution plan: {}", plan);
          return effects()
            .updateState(currentState().withPlan(plan))
            .transitionTo(EXECUTE_PLAN); // (7)
        }
      );
  }

  private static final String EXECUTE_PLAN = "execute-plan";

  private Step executePlanStep() {
    return step(EXECUTE_PLAN)
      .call(() -> {
        var stepPlan = currentState().nextStepPlan(); // (8)
        logger.debug("Executing plan step (agent:{}), asking {}", stepPlan.agentId(), stepPlan.query());
        var agentResponse = callAgent(stepPlan.agentId(), stepPlan.query()); // (9)
        if (agentResponse.startsWith("ERROR")) {
          throw new RuntimeException("Agent '" + stepPlan.agentId() + "' responded with error: " + agentResponse);
        } else {
          logger.debug("Response from [agent:{}]: '{}'", stepPlan.agentId(), agentResponse);
          return agentResponse;
        }

      })
      .andThen(String.class, answer -> {
          var newState = currentState().addAgentResponse(answer);

          if (newState.hasMoreSteps()) {
            logger.debug("Still {} steps to execute.", newState.plan().steps().size());
            return effects().updateState(newState).transitionTo(EXECUTE_PLAN); // (10)
          } else {
            logger.debug("No further steps to execute.");
            return effects().updateState(newState).transitionTo(SUMMARIZE);
          }

        }
      );
  }

  private String callAgent(String agentId, String query) {
    // We know the id of the agent to call, but not the agent class.
    // Could be WeatherAgent or ActivityAgent.
    // We can still invoke the agent based on its id, given that we know that it
    // takes a AgentRequest parameter and returns String.
    var request = new AgentRequest(currentState().userId(), query);
    DynamicMethodRef<AgentRequest, String> call =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .dynamicCall(agentId); // (9)
    return call.invoke(request);
  }
}
```

| **1** | It’s a workflow, with reliable and durable execution. |
| **2** | The steps are select - plan - execute - summarize. |
| **3** | The workflow starts by selecting agents. |
| **4** | which is performed by the `SelectorAgent`. |
| **5** | Continue with making the actual plan |
| **6** | which is performed by the `PlannerAgent`, using the selection from the previous step. |
| **7** | Continue with executing the plan. |
| **8** | Take the next task in the plan. |
| **9** | Call the agent for the task. |
| **10** | Continue executing the plan until no tasks are remaining. |
When executing the plan and calling the agents we know the id of the agent to call, but not the agent class. It can be the `WeatherAgent` or `ActivityAgent`. Therefore, we can’t use the ordinary `method` of the `ComponentClient. Instead, we use the `dynamicCall` with the id of the agent. We don’t have compile time safety for those dynamic calls, but we know that these agents take a String parameter and return AgentResponse. If we used it with the wrong types, it would be a runtime exception.

```java
private String callAgent(String agentId, String query) {
    // We know the id of the agent to call, but not the agent class.
    // Could be WeatherAgent or ActivityAgent.
    // We can still invoke the agent based on its id, given that we know that it
    // takes a AgentRequest parameter and returns String.
    var request = new AgentRequest(currentState().userId(), query);
    DynamicMethodRef<AgentRequest, String> call =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .dynamicCall(agentId); // (9)
    return call.invoke(request);
  }
```
You find the full source code for this multi-agent sample in the [akka-samples/multi-agent Github Repository](https://github.com/akka-samples/multi-agent).

## <a href="about:blank#_testing_the_agent"></a> Testing the agent

Testing agents built with Generative AI involves two complementary approaches: evaluating the quality of the non-deterministic model behavior and writing deterministic unit tests for the agent’s  and surrounding components' logic.

### <a href="about:blank#_evaluating_ai_model_quality"></a> Evaluating AI model quality

Testing AI quality is a subject that goes way beyond the scope of Akka. Testing with generative AI is difficult no matter what you’re using to implement it. Interactions with an LLM are not *deterministic*. In other words, you shouldn’t expect to get the same answer twice for the same prompt.

How can you write assertions for something like that? There are a ton of solutions, but most of them revolve around the idea that to verify an LLM’s answer, you need another LLM. This pattern is often called "LLM-as-judge". You can get an answer from one agent, and then use another agent or model to review the session history and prompts to infer, with some level of confidence, if the agent behaved the way you want it to.

For instance, after a test run, you could send the session history to a powerful model like GPT-4 with a prompt like: "Based on the user’s question about activities, did the agent correctly use the provided `getWeather` tool? Respond with only YES or NO."

There is a practice called **Agent evaluation** where developers will run single ad hoc tests or an automated battery of tests. You run your agent and then *evaluate* the results based on a number of criteria like token usage, elapsed time, and the results of using other models to infer quality metrics like accuracy or confidence.

Akka will soon have an Agent evaluation workbench that will help you run these kinds of evaluation tests.

### <a href="about:blank#_mocking_responses_from_the_model"></a> Mocking responses from the model

For predictable and repeatable tests of your agent’s business logic and component integrations, it’s essential to use deterministic responses. This allows you to verify that your agent behaves correctly when it receives a known model output.

Use the `TestKitSupport` and the `CoponentClient` to call the components from the test. The `ModelProvider` of the agents can be replaced with [TestModelProvider](_attachments/testkit/akka/javasdk/testkit/TestModelProvider.html), which provides ways to mock the responses without using the real AI model.

[AgentTeamTest.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/test/java/demo/multiagent/application/AgentTeamTest.java)
```java
public class AgentTeamTest extends TestKitSupport { // (1)

  private final TestModelProvider selectorModel = new TestModelProvider(); // (2)
  private final TestModelProvider plannerModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider summaryModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(SelectorAgent.class, selectorModel) // (3)
        .withModelProvider(PlannerAgent.class, plannerModel)
        .withModelProvider(ActivityAgent.class, activitiesModel)
        .withModelProvider(WeatherAgent.class, weatherModel)
        .withModelProvider(SummarizerAgent.class, summaryModel);
  }

  @Test
  public void test() {
    var selection = new AgentSelection(List.of("activity-agent", "weather-agent"));
    selectorModel.fixedResponse(JsonSupport.encodeToString(selection)); // (4)

    var weatherQuery = "What is the current weather in Stockholm?";
    var activityQuery = "Suggest activities to do in Stockholm considering the current weather.";
    var plan = new Plan(List.of(
        new PlanStep("weather-agent", weatherQuery),
        new PlanStep("activity-agent", activityQuery)));
    plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

    weatherModel
        .whenMessage(req -> req.equals(weatherQuery)) // (5)
        .reply("The weather in Stockholm is sunny.");

    activitiesModel
        .whenMessage(req -> req.equals(activityQuery))
        .reply("You can take a bike tour around Djurgården Park, " +
            "visit the Vasa Museum, explore Gamla Stan (Old Town)...");

    summaryModel.fixedResponse("The weather in Stockholm is sunny, so you can enjoy " +
        "outdoor activities like a bike tour around Djurgården Park, visiting the Vasa Museum, " +
        "exploring Gamla Stan (Old Town)");

    var query = "I am in Stockholm. What should I do? Beware of the weather";

    var sessionId = UUID.randomUUID().toString();
    var request = new AgentTeam.Request("alice", query);
    componentClient.forWorkflow(sessionId).method(AgentTeam::start).invoke(request); // (6)

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, SECONDS)
        .untilAsserted(() -> {
          var answer = componentClient.forWorkflow(sessionId).method(AgentTeam::getAnswer).invoke();
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

<!-- <footer> -->
<!-- <nav> -->
[Components](components/index.html) [Event Sourced Entities](event-sourced-entities.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->