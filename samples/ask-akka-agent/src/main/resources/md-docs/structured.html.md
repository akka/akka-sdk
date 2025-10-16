<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Structured responses](structured.html)

<!-- </nav> -->

# Structured responses

Many LLMs support generating outputs in a structured format, typically JSON. You can easily map such output to Java objects using the effect API.

```java
@Component(id = "activity-agent")
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

  private static final Activity DEFAULT_ACTIVITY = new Activity(
    "running",
    "Running is a great way to stay fit " +
    "and healthy. You can do it anywhere, anytime, and it requires no special equipment."
  );

  record Activity(String name, String description) {} // (2)

  public Effect<Activity> query(String message) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(message)
      .responseAs(Activity.class) // (3)
      .onFailure(throwable -> { // (4)
        if (throwable instanceof JsonParsingException) {
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
Some models, such as OpenAI and Google Gemini, have specific support for structured model responses according to a given JSON schema. To automatically include a JSON schema that corresponds to the response type you can use `responseConformsTo` instead of `responseAs`.

```java
@Component(id = "activity-agent")
public class ActivityAgentStructuredResponseSchema extends Agent {

  private static final String SYSTEM_MESSAGE = // (1)
    """
    You are an activity agent. Your job is to suggest activities in the
    real world. Like for example, a team building activity, sports, an
    indoor or outdoor game, board games, a city trip, etc.
    """.stripIndent();

  record Activity(
    @Description("Name of the activity") String name,
    @Description("Description of the activity") String description
  ) {} // (2)

  public Effect<Activity> query(String message) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(message)
      .responseConformsTo(Activity.class) // (3)
      .thenReply();
  }
}
```

| **1** | Instructions to the model doesn’t have to include details about the JSON response format. |
| **2** | `Activity` record is used to map the JSON response to a Java object. It can optionally have `akka.javasdk.annotations.Description` of the fields, which will be included in the JSON schema. |
| **3** | Use the `responseConformsTo` method  to specify the expected response type, which is also used for creating the JSON schema. |
If you still don’t get expected JSON responses from the model, you can combine those two approaches of both including the JSON schema and giving instructions about the format in the system message.

<!-- <footer> -->
<!-- <nav> -->
[Managing session memory](memory.html) [Handling failures](failures.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->