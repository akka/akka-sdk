<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Autonomous Agents](../autonomous-agents.html)
- [Defining an autonomous agent](defining.html)

<!-- </nav> -->

# Defining an autonomous agent

An [Autonomous Agent](../autonomous-agents.html) subclass implements a single `definition()` method that returns an `AgentDefinition`. The definition is the agent’s contract: it tells the runtime what tasks it accepts, which tools the model can call, and how the agent participates in multi-agent coordination.

`AgentDefinition` is built with a fluent builder API. Call `define()` to start the builder, then chain configuration methods to declare the task types the agent accepts, the tools it uses, guardrails, iteration limit, model, and optional instructions.

## <a href="about:blank#_purpose_and_instructions"></a> Purpose and instructions

The `@Component` description captures the agent’s purpose and expected outcome: a short statement of what the agent does, when to use it, and what it produces. The description is **mandatory** for an autonomous agent and is enforced at compile time. It serves three audiences from a single source of truth:

- Other agents use it to choose this agent as a delegation or handoff target.
- The runtime injects it into the model’s system message, so the agent’s own LLM understands its purpose.
- Documentation, observability, and other tooling pick it up to describe the agent.

```java
@Component(
  id = "question-answerer",
  description = "Answers questions clearly and concisely, showing reasoning step by step"
)
public class QuestionAnswerer extends AutonomousAgent {
  @Override
  public AgentDefinition definition() {
    return define().capability(TaskAcceptance.of(QuestionTasks.ANSWER));
  }
}
```
Because the description has to be understandable to a coordinator deciding whether to pick this agent, writing it well naturally produces outcome-oriented prose. Avoid procedure ("first call X, then call Y") — describe **what the agent is for**.

The agent’s behavior is then driven by its capabilities and the task types it accepts. Most autonomous agents need nothing else; their purpose comes from the description and their behavior emerges from the tasks, tools, and coordination structure.

### <a href="about:blank#_optional_instructions"></a> Optional instructions

When you need to shape **how** the model speaks or judges, beyond the outcome statement in the description, add `instructions(…​)` on the definition. Use this for tone, persona, role, or domain rules:

```java
define()
  .instructions("Respond in formal English. Always cite sources for factual claims.")
  .capability(TaskAcceptance.of(QuestionTasks.ANSWER));
```
The runtime appends instructions to the system message alongside the description.

Procedural guidance on how the LLM should approach a task is fine here — "read the input carefully, identify key claims, then check them against the supporting evidence" is the kind of prompt engineering instructions are for.

What does **not** belong in instructions is multi-agent orchestration: when to delegate, when to hand off, who to message. Those mechanics are derived automatically from the capabilities and from the `@Component` descriptions of the participating agents. If you find yourself writing "delegate to Researcher first, then to Analyst, then synthesize" as instructions, the work belongs in capabilities and task definitions instead. See [Coordination patterns](coordination.html) for how to decompose multi-agent work.

## <a href="about:blank#_accepted_task_types"></a> Accepted task types

An agent declares which task types it can work on by adding a `TaskAcceptance` capability. The agent will only process tasks whose definition matches one of the accepted types.

```java
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;

define()
  .capability(
    TaskAcceptance.of(PipelineTasks.COLLECT, PipelineTasks.ANALYZE, PipelineTasks.REPORT));
```
See [Tasks](tasks.html) for how task types are defined.

## <a href="about:blank#_tools"></a> Tools

An agent can expose tools to the model in three ways:

- **Methods on a separate tool class**, registered with `tools(…​)`. The class can be supplied as an object instance, or as a `Class` that the runtime instantiates via the configured `DependencyProvider`.
- **`@FunctionTool` methods on the agent class itself.** These are discovered automatically; no `tools(…​)` call is needed.
- **Akka components as tools.** Pass the component `Class` (not an instance) to `tools(…​)` to let the model invoke command handlers on Entities, Workflows, or Views.

### <a href="about:blank#_tool_class"></a> Tool class

Domain tools are added with `tools()`. Each element can be an object instance or a `Class`. Pass a `Class` if you want the runtime to instantiate it via the configured `DependencyProvider`. The class can then receive injected dependencies like `ComponentClient`.

Methods on tool objects must be annotated with `@FunctionTool`. The description is included in the model’s context to guide tool selection.

[ConsultingTools.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/consulting/application/ConsultingTools.java)
```java
public class ConsultingTools {

  @FunctionTool(description = "Perform a preliminary assessment of a client problem.")
  public String assessProblem(String problemDescription) {
    return (
      "Preliminary assessment for '" +
      problemDescription +
      "': " +
      "Complexity: moderate. Involves integration challenges and process redesign. " +
      "Estimated scope: 3-6 months. Key risks: legacy system dependencies, " +
      "change management resistance."
    );
  }

  @FunctionTool(
    description = "Check if a problem exceeds standard consulting scope and needs escalation."
  )
  public String checkComplexity(String assessment) {
    if (
      assessment.toLowerCase().contains("regulatory") ||
      assessment.toLowerCase().contains("merger")
    ) {
      return (
        "COMPLEX: This problem involves regulatory or M&A considerations " +
        "that exceed standard consulting scope. Recommend escalation to senior consultant."
      );
    }
    return (
      "STANDARD: This problem is within standard consulting scope. " +
      "Can be handled with research and analysis."
    );
  }
}
```
Register the tool object in the definition:

```java
define()
  .tools(new ConsultingTools())
```

### <a href="about:blank#_tools_on_the_agent_class"></a> Tools on the agent class

Tools can also be defined directly on the agent class as `@FunctionTool` methods. These are discovered automatically and do not need to be registered via `tools()` in the definition.

[ReportAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/pipeline/application/ReportAgent.java)
```java
@Component(
  id = "report-agent",
  description = """
  Processes report phases: collects data, analyzes findings, \
  produces comprehensive reports\
  """
)
public class ReportAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(
        TaskAcceptance.of(
          PipelineTasks.COLLECT,
          PipelineTasks.ANALYZE,
          PipelineTasks.REPORT
        ).maxIterationsPerTask(5)
      );
  }

  @FunctionTool(description = "Collect data on a topic and return findings")
  public String collectData(String topic) {
    return "Collected data on: " + topic;
  }

  @FunctionTool(description = "Analyze data and return analysis")
  public String analyzeData(String data) {
    return "Analysis of: " + data;
  }
}
```

### <a href="about:blank#_akka_components_as_tools"></a> Akka components as tools

Components, including Workflows, Event Sourced Entities, Key Value Entities, and Views, can also be used as tools. Pass the component `Class` (not an instance) to `tools()`. See [Using Akka components as function tools](../agents/extending.html#component_tools) for details, and [Extending agents with function tools](../agents/extending.html) for the broader reference on tool annotations, parameter descriptions, and return types.

## <a href="about:blank#_mcp_tools"></a> MCP tools

Remote MCP (Model Context Protocol) tool endpoints are added with `mcpTools()`.

```java
define()
  .mcpTools(RemoteMcpTools.create("https://mcp.example.com/tools"))
```
See [Using tools from remote MCP servers](../agents/extending.html#mcp_tools) for details on configuring MCP tool endpoints.

## <a href="about:blank#_guardrails"></a> Guardrails

Request and response guardrails constrain the model interaction at each iteration. Request guardrails evaluate prompts before they are sent to the model. Response guardrails evaluate responses received from the model.

```java
define()
  .requestGuardrails(MyRequestGuardrail.class)
  .responseGuardrails(MyResponseGuardrail.class)
```
See [Guardrails](../agents/guardrails.html) for details on implementing guardrail classes.

## <a href="about:blank#_iteration_limit"></a> Iteration limit

`maxIterationsPerTask()` on the `TaskAcceptance` capability sets the maximum number of model iterations before the agent fails the current task. The default is configured via `akka.javasdk.agent.autonomous.max-iterations-per-task`. Set this based on the expected complexity of the work. Simple tasks may need only 3 iterations, while complex coordination may need more.

```java
TaskAcceptance.of(MyTasks.TASK).maxIterationsPerTask(5)
```
Right-size the budget to the agent’s role. A triage or classifier agent that only needs to read the input and pick a target can run on a low budget (around 3). A resolver or synthesizer that calls tools, reasons over results, and produces a structured output usually needs more (5 or higher). The `support` sample illustrates this split: the triage agent uses `maxIterationsPerTask(3)` while the billing and technical specialists use `maxIterationsPerTask(5)`.

As the iteration budget approaches its limit, the runtime injects a reminder into the model’s context. The reminder reads along the lines of "you have used N of M iterations, complete or fail the current task soon". It is also surfaced as a `Notification.TaskApproachingMaxIterations` event on the agent’s notification stream.

## <a href="about:blank#_model"></a> Model

By default, the agent uses the model configured in `application.conf` (see [Configuring the model](../agents.html#model)). Override with `modelProvider()` to use a different model for this agent.

```java
define()
  .modelProvider(ModelProvider.openAi().withModel("gpt-4o"))
```

## <a href="about:blank#_complete_definition_example"></a> Complete definition example

A coordinator that combines a description, domain tools, an accepted task type with handoff to a senior consultant, and delegation to two specialist agents:

[ConsultingCoordinator.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/consulting/application/ConsultingCoordinator.java)
```java
@Component(
  id = "consulting-coordinator",
  description = """
  Delivers actionable consulting recommendations by assessing \
  problem complexity and routing to the right expertise level\
  """
)
public class ConsultingCoordinator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .tools(new ConsultingTools())
      .capability(
        TaskAcceptance.of(ConsultingTasks.ENGAGEMENT).canHandoffTo(SeniorConsultant.class)
      )
      .capability(Delegation.to(ConsultingResearcher.class).maxParallelWorkers(2))
      .capability(Delegation.to(FactCheckAgent.class));
  }
}
```
The combined definition declares everything the runtime needs to build the system message, expose tools to the model, and enforce iteration and coordination limits. See [Coordination capabilities](capabilities.html) for the coordination capabilities used here.

## <a href="about:blank#dynamic-configuration"></a> Dynamic configuration

`AgentSetup` overrides parts of the definition for a single instance. The agent class declares whatever defaults make sense in `definition()`, and the call site supplements or overrides specific fields through `AgentSetup` before assigning work. Anything supplied through `AgentSetup` takes precedence over the corresponding field from `definition()`. Everything else falls back to the static definition.

`AgentSetup.create()` supports `instructions(…​)` and `capability(…​)`. These are the two pieces of the definition that are most useful to vary per instance. The `@Component` description is bound to the component class and cannot be overridden per instance. Tools, model provider, and guardrails are declared statically in `definition()` and also cannot be overridden through `AgentSetup`. Call `setup(…​)` once per instance, before `runSingleTask`, `assignTasks`, or any other client operation that triggers iteration.

A typical use is to keep the static defaults in `definition()` (guardrails, model provider, the standard tool set) and override only the parts that change per instance. For example, instructions tailored to the user’s request, or an extra accepted task type for that particular call.

At the extreme, the definition can be a near-empty shell and everything comes from the client:

[DynamicAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/dynamic/application/DynamicAgent.java)
```java
@Component(
  id = "dynamic-agent",
  description = "Generic agent configured dynamically per request via AgentSetup"
)
public class DynamicAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define();
  }
}
```
The endpoint then configures each instance just before assigning a task. The same `DynamicAgent` class is reused for both the summarization and translation flows; only the `AgentSetup` differs:

[DynamicEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/dynamic/api/DynamicEndpoint.java)
```java
@Post("/summarize")
public TaskResponse summarize(TaskRequest request) {
  var agentId = requestContext()
    .queryParams()
    .getString("runId")
    .filter(s -> !s.isBlank())
    .orElseGet(() -> UUID.randomUUID().toString());

  componentClient
    .forAutonomousAgent(DynamicAgent.class, agentId)
    .setup(
      AgentSetup.create()
        .instructions(
          "Produce a concise summary of the given content, highlighting key points."
        )
        .capability(TaskAcceptance.of(DynamicTasks.SUMMARIZE))
    );

  var taskId = componentClient
    .forAutonomousAgent(DynamicAgent.class, agentId)
    .runSingleTask(DynamicTasks.SUMMARIZE.instructions(request.content()));
  return new TaskResponse(taskId, agentId, "dynamic-agent");
}
```
This pattern fits when many task variants share the same execution shape and the differences are best expressed as data rather than as separate agent classes: different instructions over the same task types, runtime-supplied capability sets, or tool sets that depend on the user’s permissions.

## <a href="about:blank#attachment-content"></a> Loading attachment content

A task can carry image or PDF content as [attachments](tasks.html#_creating_task_instances). An attachment holds a URI reference, not the bytes, so it stays small as the task moves between agents. The runtime resolves the reference to the actual content just before the agent sends it to the model.

Attach content to a task by URI:

```java
var task = ReviewTasks.REVIEW
  .instructions("Describe this diagram")
  .attach(MessageContent.ImageMessageContent.fromUri("https://example.com/diagram.png"));
```
For the built-in URI schemes the runtime resolves the reference automatically, with no extra configuration:

- `http(s)://` is fetched over HTTP. Some models can fetch public URLs themselves, but resolving them in the runtime works regardless of the model.
- `object://bucket/key` is loaded from a configured [object storage](../integrations/object-storage.html) bucket.
For object storage, build the reference with `ImageUrlMessageContent.create(bucket, key)` or `PdfUrlMessageContent.create(bucket, key)` and attach it to the task:

```java
var imageBucket = objectStorageProvider.forBucket("images");
imageBucket.put(key, body.getData(), body.getContentType());

var task = ReviewTasks.REVIEW
  .instructions("Describe this image")
  .attach(MessageContent.ImageUrlMessageContent.create(imageBucket, key));
```
See [Object storage](../integrations/object-storage.html) for how to configure buckets and how the backend behaves in dev mode and tests.

### <a href="about:blank#_custom_content_loading"></a> Custom content loading

When content lives behind an authenticated endpoint, in a private storage system, or under a custom URI scheme, implement a `ContentLoader` and register it on the definition with `contentLoader(…​)`:

```java
define()
  .capability(TaskAcceptance.of(ReviewTasks.REVIEW))
  .contentLoader(new MyContentLoader());
```
The `ContentLoader` interface has a single `load` method that receives a `LoadableMessageContent`. Use pattern matching to handle each content type, fetch the data, and return it with the appropriate MIME type. The interface is the same one used by request-based agents, so the implementation carries over unchanged. See the [custom content loading example](../agents/prompt.html#custom-content-loading) for a full `ContentLoader` implementation.

When a task attachment uses a URI that the loader recognizes, the runtime calls `load` with the matching `LoadableMessageContent` and forwards the resolved bytes to the model in place of the URI.

Unlike a request-based agent, where the loader can be created per request to carry per-request credentials, the autonomous agent’s loader is part of the static definition and is shared across every task the agent runs. It cannot be overridden through [AgentSetup](about:blank#dynamic-configuration), so make the implementation thread-safe: concurrent task executions may call it at the same time.

|  | If the `load` method throws an exception, the current task fails. |

## <a href="about:blank#_see_also"></a> See Also

- [Tasks](tasks.html)
- [Coordination capabilities](capabilities.html)
- [Extending agents with function tools](../agents/extending.html)
- [Guardrails](../agents/guardrails.html)

<!-- <footer> -->
<!-- <nav> -->
[Autonomous Agents](../autonomous-agents.html) [Tasks](tasks.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->