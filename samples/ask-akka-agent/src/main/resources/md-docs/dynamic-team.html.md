<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [AI Planner Part 6: Dynamic orchestration](dynamic-team.html)

<!-- </nav> -->

# AI Planner Part 6: Dynamic orchestration

[1: The activity agent](index.html) > [2: User preferences](preferences.html) > [3: Weather agent](weather.html) > [4: Orchestrate the agents](team.html) > [5: List by user](list.html) > **6: Dynamic orchestration**

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

We have used a workflow with predefined steps to call the `WeatherAgent` followed by the `ActivityAgent`. In a larger system there can be many agents, and it would be cumbersome to define a single workflow that would handle all types of requests. A more flexible approach is to let the AI model come up with a plan of which agents to use and in which order to achieve the goal of the request.

In this part of the guide you will:

- Add agents to create a dynamic plan
- Use a workflow that executes the plan

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_planner_agents"></a> Planner agents

We split the planning into two steps and use two separate agents for these tasks. It’s not always necessary to use several steps for the planning. You have to experiment with what works best for your problem domain.

1. Select agents that are useful for a certain problem.
2. Decide in which order to use the agents and give each agent precise instructions for its task.
The `SelectorAgent` decides which agents to use. Add a new file `SelectorAgent.java` to `src/main/java/com/example/application/`

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

Add the `@AgentDescription` to the `WeatherAgent`:

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
Add the `@AgentDescription` to the `ActivityAgent`:

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
![steps 2](../../concepts/_images/steps-2.svg)
of the `SelectorAgent` we retrieve a subset of the agents with a certain role. This role is also defined in the `@AgentDescription` annotation.

The result from the `SelectorAgent` is a list of agent ids. Add a new file `AgentSelection.java` to `src/main/java/com/example/domain/`

[AgentSelection.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/domain/AgentSelection.java)
```java
public record AgentSelection(List<String> agents) {
}
```
After selecting agents, we use a `PlannerAgent` to decide in which order to use the agents and the precise request each agent should receive to perform its single task. Add a new file `PlannerAgent.java` to `src/main/java/com/example/application/`

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

## <a href="about:blank#_common_signature_of_worker_agents"></a> Common signature of worker agents

We will call the selected agents according to the plan, and we want to do that without explicitly knowing which agent to call. For this, the worker agents (`WeatherAgent` and `ActivityAgent`) must have the same shape. Adjust the `ActivityAgent` to have this method signature:

ActivityAgent.java
```java
public Effect<String> query(AgentRequest request) {
```
Where `AgentRequest` is a new record. Add a new file `AgentRequest.java` to `src/main/java/com/example/domain/`

[AgentRequest.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/domain/AgentRequest.java)
```java
public record AgentRequest(String userId, String message) {}
```
Remove the previous `ActivityAgent.Request`, and update all references to use the new `AgentRequest` instead.

Make the same changes to the `WeatherAgent`. Use the same method signature and use the `AgentRequest` record.

## <a href="about:blank#_execute_the_plan"></a> Execute the plan

`SelectorAgent` and `PlannerAgent` are the two agents that perform the planning, but we also need to connect them and execute the plan. This orchestration is the job of the workflow.

Update the `AgentTeam` to this:

[AgentTeam.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/AgentTeam.java)
```java
@ComponentId("agent-team")
public class AgentTeam extends Workflow<AgentTeam.State> { // (1)
  public record Request(String userId, String message) {}


  enum Status {
    STARTED,
    COMPLETED,
    FAILED,
  }

  public record State(
      String userId,
      String userQuery,
      Plan plan,
      String finalAnswer,
      Map<String, String> agentResponses,
      Status status) {

    public static State init(String userId, String query) {
      return new State(userId, query, new Plan(), "", new HashMap<>(), STARTED);
    }


    public State withFinalAnswer(String answer) {
      return new State(userId, userQuery, plan, answer, agentResponses, status);
    }

    public State addAgentResponse(String response) {
      // when we add a response, we always do it for the agent at the head of the plan queue
      // therefore we remove it from the queue and proceed
      var agentId = plan.steps().removeFirst().agentId();
      agentResponses.put(agentId, response);
      return this;
    }

    public PlanStep nextStepPlan() {
      return plan.steps().getFirst();
    }

    public boolean hasMoreSteps() {
      return !plan.steps().isEmpty();
    }

    public State withPlan(Plan plan) {
      return new State(userId, userQuery, plan, finalAnswer, agentResponses, STARTED);
    }

    public State complete() {
      return new State(userId, userQuery, plan, finalAnswer, agentResponses, COMPLETED);
    }

    public State failed() {
      return new State(userId, userQuery, plan, finalAnswer, agentResponses, FAILED);
    }

  }

  private static final Logger logger = LoggerFactory.getLogger(AgentTeam.class);

  private final ComponentClient componentClient;

  public AgentTeam(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

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

  public ReadOnlyEffect<String> getAnswer() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else {
      return effects().reply(currentState().finalAnswer());
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

  private static final String SUMMARIZE = "summarize";

  private Step summarizeStep() {
    return step(SUMMARIZE)
      .call(() -> {
        var agentsAnswers = currentState().agentResponses.values();
        return componentClient.forAgent().inSession(sessionId()).method(SummarizerAgent::summarize)
                .invoke(new SummarizerAgent.Request(currentState().userQuery, agentsAnswers));
      })
      .andThen(String.class, finalAnswer ->
        effects().updateState(currentState().withFinalAnswer(finalAnswer).complete()).end());
  }

  private static final String INTERRUPT = "interrupt";

  private Workflow.Step interruptStep() {
    return step(INTERRUPT)
      .call(() -> {
        logger.debug("Interrupting workflow");
        return Done.getInstance();
      })
      .andThen(() -> effects().updateState(currentState().failed()).end());
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
}
```

| **1** | It’s a workflow, with reliable and durable execution. |
| **2** | The steps are select - plan - execute - summarize. |
| **3** | The workflow starts by selecting agents |
| **4** | which is performed by the `SelectorAgent`. |
| **5** | Continue with making the actual plan |
| **6** | which is performed by the `PlannerAgent`, using the selection from the previous step. |
| **7** | Continue with executing the plan. |
| **8** | Take the next task in the plan. |
| **9** | Call the agent for the task. |
| **10** | Continue executing the plan until no tasks are remaining. |
When executing the plan and calling the agents we know the id of the agent to call, but not the agent class. It can be the `WeatherAgent` or `ActivityAgent`. Therefore, we can’t use the ordinary `method` of the `ComponentClient. Instead, we use the `dynamicCall` with the id of the agent. This is the reason why we had to align the method signatures of the worker agents.

This also ends the workflow by creating a summary of the results from the involved agent. Add a new file `SummarizerAgent.java` to `src/main/java/com/example/application/`

[SummarizerAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/SummarizerAgent.java)
```java
@ComponentId("summarizer-agent")
@AgentDescription(
    name = "Summarizer",
    description = "An agent that creates a summary from responses provided by other agents")
public class SummarizerAgent extends Agent {
  public record Request(String originalQuery, Collection<String> agentsResponses) {}

  private String buildSystemMessage(String userQuery) {
    return  """
        You will receive the original query and a message generate by different other agents.
      
        Your task is to build a new message using the message provided by the other agents.
        You are not allowed to add any new information, you should only re-phrase it to make
        them part of coherent message.
      
        The message to summarize will be provided between single quotes.
      
        ORIGINAL USER QUERY:
        %s
      """.formatted(userQuery);
  }

  public Agent.Effect<String> summarize(Request request) {
    var allResponses = request.agentsResponses.stream()
        .filter(response -> !response.startsWith("ERROR"))
        .collect(Collectors.joining(" "));

    return effects()
        .systemMessage(buildSystemMessage(request.originalQuery))
        .userMessage("Summarize the following message: '" + allResponses + "'")
        .thenReply();
  }

}
```
We still only have the two worker agents `WeatherAgent` and `ActivityAgent`, but you can add more agents to this structure of dynamic planning and execution, and it will be able to solve other types of problems without changing the orchestration engine.

## <a href="about:blank#_running_the_service"></a> Running the service

Start your service locally:

```command
mvn compile exec:java
```
Ask for activities:

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do? Beware of the weather."}'
```
Retrieve the suggested activities with the `sessionId` from the previous response:

```command
curl -i -XGET --location "http://localhost:9000/activities/alice/{sessionId}"
```

## <a href="about:blank#_next_steps"></a> Next steps

Congratulations, you have completed the tour of building a multi-agent system. Now you can take your Akka skills to the next level:

- **Expand on your own**: Learn more details of the [Akka components](../../java/components/index.html) to enhance your application with additional features.
- **Explore other Akka samples**: Discover more about Akka by exploring [different use cases](../samples.html) for inspiration.

<!-- <footer> -->
<!-- <nav> -->
[AI Planner Part 5: List by user](list.html) [Samples](../samples.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->