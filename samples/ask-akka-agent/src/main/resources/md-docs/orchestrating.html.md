<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Orchestrating multiple agents](orchestrating.html)

<!-- </nav> -->

# Orchestrating multiple agents

A single agent performs one well-defined task. Several agents can collaborate to achieve a common goal. The agents should be orchestrated from a predefined workflow or a dynamically created plan.

## <a href="about:blank#_using_a_predefined_workflow"></a> Using a predefined workflow

Let’s first look at how to define a workflow that orchestrates several agents in a predefined steps. This is similar to the <a href="calling.html">`ActivityAgentManager`</a> that was illustrated above, but it uses both the `WeatherAgent` and the `ActivityAgent`. First it retrieves the weather forecast and then it finds suitable activities.

```java
@Component(id = "agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> {

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  public record State(String userQuery, String weatherForecast, String answer) {
    State withWeatherForecast(String f) {
      return new State(userQuery, f, answer);
    }

    State withAnswer(String a) {
      return new State(userQuery, weatherForecast, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) {
    return effects()
      .updateState(new State(query, "", ""))
      .transitionTo(AgentTeamWorkflow::askWeather) // (1)
      .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().answer.isEmpty()) {
      String workflowId = commandContext().workflowId();
      return effects().error("Workflow '" + workflowId + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      .stepTimeout(AgentTeamWorkflow::askWeather, ofSeconds// (60))
      .stepTimeout(AgentTeamWorkflow::suggestActivities, ofSeconds// (60))
      .defaultStepRecovery(maxRetries// (2).failoverTo(AgentTeamWorkflow::error))
      .build();
  }

  @StepName("weather")
  private StepEffect askWeather() { // (2)
    var forecast = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(WeatherAgent::query)
      .invoke(currentState().userQuery);

    logger.info("Weather forecast: {}", forecast);

    return stepEffects()
      .updateState(currentState().withWeatherForecast(forecast)) // (3)
      .thenTransitionTo(AgentTeamWorkflow::suggestActivities);
  }

  @StepName("activities")
  private StepEffect suggestActivities() {
    var request = // (4)
      currentState().userQuery +
        "\nWeather forecast: " + currentState().weatherForecast;

    var suggestion = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(ActivityAgent::query)
      .invoke(request);

    logger.info("Activities: {}", suggestion);

    return stepEffects()
      .updateState(currentState().withAnswer(suggestion)) // (5)
      .thenEnd();
  }

  private StepEffect error() {
    return stepEffects().thenEnd();
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
![steps 4](../../concepts/_images/steps-4.svg)
we explicitly include the forecast in the request to the `ActivityAgent`. That is not strictly necessary because the agents share the same session memory and thereby the `ActivityAgent` will already have the weather forecast in the context that is sent to the AI model.

The workflow will automatically execute the steps in a reliable and durable way. This means that if a call in a step fails, it will be retried until it succeeds or the retry limit of the recovery strategy is reached and separate error handling can be performed. The state machine of the workflow is durable, which means that if the workflow is restarted for some reason it will continue from where it left off, i.e. execute the current non-completed step again.

## <a href="about:blank#_creating_dynamic_plans"></a> Creating dynamic plans

To create a more flexible and autonomous agentic system you want to analyze the problem and dynamically come up with a plan. The agentic system should identify the tasks to achieve the goal by itself. Decide which agents to use and in which order to execute them. Coordinate input and output between agents and adjust the plan along the way.

There are several approaches for the planning, such as using deterministic algorithms or using AI also for the planning. We will look at how we can use AI for analyzing a request, selecting agents and in which order to use them.

We split the planning into two steps and use two separate agents for these tasks. It’s not always necessary to use several steps for the planning. You have to experiment with what works best for your problem domain.

1. Select agents that are useful for a certain problem.
2. Decide in which order to use the agents and give each agent precise instructions for its task.
The `SelectorAgent` decides which agents to use:

[SelectorAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/SelectorAgent.java)
```java
@Component(
  id = "selector-agent",
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
      Your job is to analyse the user request and select the agents that should be
      used to answer the user. In order to do that, you will receive a list of
      available agents. Each agent has an id, a name and a description of its capabilities.

      For example, a user may be asking to book a trip. If you see that there is a
      weather agent, a city trip agent and a hotel booking agent, you should select
      those agents to complete the task. Note that this is just an example. The list
      of available agents may vary, so you need to use reasoning to dissect the original
      user request and using the list of available agents,
      decide which agents must be selected.

      You don't need to come up with an execution order. Your task is to
      analyze user's request and select the agents.

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
    """.stripIndent()
      .formatted(JsonSupport.encodeToString(agents)); // (3)
  }

  public Effect<AgentSelection> selectAgents(String message) {
    return effects()
      .systemMessage(systemMessage)
      .userMessage(message)
      .responseConformsTo(AgentSelection.class)
      .thenReply();
  }
}
```

| **1** | The `AgentRegistry` contains information about all agents. |
| **2** | Select the agents with the role `"worker"`. |
| **3** | Detailed instructions and include descriptions (as json) of the agents. |
The information about the agents in the `AgentRegistry` comes from the `@Component` and `@AgentRole` annotations.
When using it for planning like this, it is important that the agents define those descriptions that the LLM can use to come up with a good plan.

The `WeatherAgent` has:

[WeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherAgent.java)
```java
@Component(
  id = "weather-agent",
  name = "Weather Agent",
  description = """
    An agent that provides weather information. It can provide current weather,
    forecasts, and other related information.
  """
)
@AgentRole("worker")
public class WeatherAgent extends Agent {
```
The `ActivityAgent` has:

[ActivityAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/ActivityAgent.java)
```java
@Component(
  id = "activity-agent",
  name = "Activity Agent",
  description = """
    An agent that suggests activities in the real world. Like for example,
    a team building activity, sports, an indoor or outdoor game,
    board games, a city trip, etc.
  """
)
@AgentRole("worker")
public class ActivityAgent extends Agent {
```
Note that in
![steps 2](../../concepts/_images/steps-2.svg)
of the `Selector` we retrieve a subset of the agents with a certain
 role. This role is also defined in the `@AgentRole` annotation.

The result from the `Selector` is a list of agent ids:

[AgentSelection.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/domain/AgentSelection.java)
```java
public record AgentSelection(List<String> agents) {}
```
After selecting agents, we use a `PlannerAgent` to decide in which order to use the agents and what precise request that each agent should receive to perform its single task.

[PlannerAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/PlannerAgent.java)
```java
@Component(
  id = "planner-agent",
  name = "Planner",
  description = """
  An agent that analyzes the user request and available agents to plan the tasks
  to produce a suitable answer.
  """
)
public class PlannerAgent extends Agent {

  public record Request(String message, AgentSelection agentSelection) {}

  private final AgentRegistry agentsRegistry;

  public PlannerAgent(AgentRegistry agentsRegistry) {
    this.agentsRegistry = agentsRegistry;
  }

  private String buildSystemMessage(AgentSelection agentSelection) {
    var agents = agentSelection.agents().stream().map(agentsRegistry::agentInfo).toList(); // (1)
    return """
      Your job is to analyse the user request and the list of agents and devise the
      best order in which the agents should be called in order to produce a
      suitable answer to the user.

      You can find the list of exiting agents below (in JSON format):
      %s

      Note that each agent has a description of its capabilities.
      Given the user request, you must define the right ordering.

      Moreover, you must generate a concise request to be sent to each agent.
      This agent request is of course based on the user original request,
      but is tailored to the specific agent. Each individual agent should not
      receive requests or any text that is not related with its domain of expertise.

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
        .responseConformsTo(Plan.class)
        .thenReply();
    }
  }
}
```

| **1** | Lookup the agent information for the selected agents from the `AgentRegistry. |
| **2** | Detailed instructions and include descriptions (as json) of the agents. |
That’s the two agents that perform the planning, but we also need to connect them and execute the plan. This orchestration is the job of a workflow, called `AgentTeamWorkflow`.

[AgentTeamWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/AgentTeamWorkflow.java)
```java
@Component(id = "agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> { // (1)

  public record Request(String userId, String message) {}


  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      .defaultStepTimeout(ofSeconds// (30))
      .defaultStepRecovery(maxRetries// (1).failoverTo(AgentTeamWorkflow::interruptStep))
      .stepRecovery(
        AgentTeamWorkflow::selectAgentsStep,
        maxRetries// (1).failoverTo(AgentTeamWorkflow::summarizeStep)
      )
      .build();
  }

  public Effect<Done> start(Request request) {
    if (currentState() == null) {
      return effects()
        .updateState(State.init(request.userId(), request.message()))
        .transitionTo(AgentTeamWorkflow::selectAgentsStep) // (3)
        .thenReply(Done.getInstance());
    } else {
      return effects()
        .error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }

  @StepName("select-agents")
  private StepEffect selectAgentsStep() { // (2)
    var selection = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(SelectorAgent::selectAgents)
      .invoke(currentState().userQuery); // (4)

    logger.info("Selected agents: {}", selection.agents());
    if (selection.agents().isEmpty()) {
      var newState = currentState()
        .withFinalAnswer("Couldn't find any agent(s) able to respond to the original query.")
        .failed();
      return stepEffects().updateState(newState).thenEnd(); // terminate workflow
    } else {
      return stepEffects()
        .thenTransitionTo(AgentTeamWorkflow::createPlanStep)
        .withInput(selection); // (5)
    }
  }

  @StepName("create-plan")
  private StepEffect createPlanStep(AgentSelection agentSelection) { // (2)
    logger.info(
      "Calling planner with: '{}' / {}",
      currentState().userQuery,
      agentSelection.agents()
    );

    var plan = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(PlannerAgent::createPlan)
      .invoke(new PlannerAgent.Request(currentState().userQuery, agentSelection)); // (6)

    logger.info("Execution plan: {}", plan);
    return stepEffects()
      .updateState(currentState().withPlan(plan))
      .thenTransitionTo(AgentTeamWorkflow::executePlanStep); // (7)
  }

  @StepName("execute-plan")
  private StepEffect executePlanStep() { // (2)
    var stepPlan = currentState().nextStepPlan(); // (8)
    logger.info(
      "Executing plan step (agent:{}), asking {}",
      stepPlan.agentId(),
      stepPlan.query()
    );
    var agentResponse = callAgent(stepPlan.agentId(), stepPlan.query()); // (9)
    if (agentResponse.startsWith("ERROR")) {
      throw new RuntimeException(
        "Agent '" + stepPlan.agentId() + "' responded with error: " + agentResponse
      );
    } else {
      logger.info("Response from [agent:{}]: '{}'", stepPlan.agentId(), agentResponse);
      var newState = currentState().addAgentResponse(agentResponse);

      if (newState.hasMoreSteps()) {
        logger.info("Still {} steps to execute.", newState.plan().steps().size());
        return stepEffects()
          .updateState(newState)
          .thenTransitionTo(AgentTeamWorkflow::executePlanStep); // (10)
      } else {
        logger.info("No further steps to execute.");
        return stepEffects()
          .updateState(newState)
          .thenTransitionTo(AgentTeamWorkflow::summarizeStep);
      }
    }
  }

  private String callAgent(String agentId, String query) {
    // We know the id of the agent to call, but not the agent class.
    // Could be WeatherAgent or ActivityAgent.
    // We can still invoke the agent based on its id, given that we know that it
    // takes an AgentRequest parameter and returns String.
    var request = new AgentRequest(currentState().userId(), query);
    DynamicMethodRef<AgentRequest, String> call = componentClient
      .forAgent()
      .inSession(sessionId())
      .dynamicCall(agentId); // (9)
    return call.invoke(request);
  }


  @StepName("summarize")
  private StepEffect summarizeStep() { // (2)
    var agentsAnswers = currentState().agentResponses.values();
    var finalAnswer = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(SummarizerAgent::summarize)
      .invoke(new SummarizerAgent.Request(currentState().userQuery, agentsAnswers));

    return stepEffects()
      .updateState(currentState().withFinalAnswer(finalAnswer).complete())
      .thenPause();
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
  // takes an AgentRequest parameter and returns String.
  var request = new AgentRequest(currentState().userId(), query);
  DynamicMethodRef<AgentRequest, String> call = componentClient
    .forAgent()
    .inSession(sessionId())
    .dynamicCall(agentId); // (9)
  return call.invoke(request);
}
```
You find the full source code for this multi-agent sample in the [akka-samples/multi-agent GitHub Repository](https://github.com/akka-samples/multi-agent).

<!-- <footer> -->
<!-- <nav> -->
[Streaming responses](streaming.html) [Guardrails](guardrails.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->