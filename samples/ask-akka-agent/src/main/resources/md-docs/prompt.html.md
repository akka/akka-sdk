<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Choosing the prompt](prompt.html)

<!-- </nav> -->

# Choosing the prompt

The prompt consists of essential instructions to the model.

- The system message provides system-level instructions to the AI model that defines its behavior and context. The system message acts as a foundational prompt that establishes the AI’s role, constraints, and operational parameters. It is processed before user messages and helps maintain consistent behavior throughout the interactions.
- The user message represents the specific query, instruction, or input that will be processed by the model to generate a response.
An agent that suggests real-world activities may have a prompt like:

[ActivityAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/ActivityAgent.java)
```java
@Component(id = "activity-agent")
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
      .userMessage(message) // (3)
      .thenReply();
  }
}
```

| **1** | Define the system message as a constant, but it could also be a method that adapts the system message based on the request. |
| **2** | Use the system message in the effect builder. |
| **3** | Define the user message for the specific request, and use in the effect builder. |
Keep in mind that some models have preferences in how you wrap or label user input within the system prompt and you’ll need to take that into account when defining your system message.

## <a href="about:blank#_using_dynamic_prompts_with_templates"></a> Using dynamic prompts with templates

As an alternative to hard-coded prompts, there is a built-in prompt template entity. The advantage of using the prompt template entity is that you can change the prompts at runtime without restarting or redeploying the service. Because the prompt template is managed as an entity, you retain full change history.

ActivityAgent.java
```java
@Component(id = "activity-agent")
public class ActivityAgentWithTemplate extends Agent {

  public Effect<String> query(String message) {
    return effects()
      .systemMessageFromTemplate("activity-agent-prompt") // (1)
      .userMessage(message) //
      .thenReply();
  }
}
```

| **1** | Define the system message prompt template key. |
In addition to the prompt template key you can optionally add parameters to `systemMessageFromTemplate`. Those will be used to format the template with `java.util.Formatter`.

Prompts are stored in the `PromptTemplate` [Event Sourced Entity](../event-sourced-entities.html). This is a built-in entity, automatically registered at runtime if there are any Agent components in the service. To initialize the prompt or get the current value you can use component client the same way as for any other entity.

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
Keeping the prompt in the Event Sourced Entity lets you see the history of all changes. It’s also possible to subscribe to changes in the prompt template entity, so that you can build a [View](../views.html) or react to changes in the prompt.

The following table describes all of the methods available for the `PromptTemplate` entity:

| Method | Description |
| --- | --- |
| `init` | Initializes the prompt template with a given value. If the prompt template already exists, it will not change it. Useful for setting the initial value, e.g. in the `onStartup` method of the [ServiceSetup](../setup-and-dependency-injection.html#_service_lifecycle). |
| `update` | Updates the prompt template with a new value. If the prompt template does not exist, it will create it. If the value is the same as the current value, it will not change it. |
| `get` | Retrieves the current value of the prompt template. If the prompt template does not exist, it will throw an exception. |
| `getOptional` | Retrieves the current value of the prompt template as an `Optional`. If the prompt template does not exist, it will return an empty `Optional`. |
| `delete` | Deletes the prompt template. |
Although the system message has a dedicated method to use the prompt template, you can also use it for the user message. In that case you have to use the component client to retrieve the current value of the prompt template and pass it as the user message.

## <a href="about:blank#_adding_more_context"></a> Adding more context

[Retrieval-Augmented Generation (RAG)](../rag.html) is a technique to provide additional, relevant content in the user message.

<!-- <footer> -->
<!-- <nav> -->
[Agents](../agents.html) [Calling agents](calling.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->