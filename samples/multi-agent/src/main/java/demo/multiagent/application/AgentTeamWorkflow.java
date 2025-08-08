package demo.multiagent.application;

import static demo.multiagent.application.AgentTeamWorkflow.Status.*;
import static demo.multiagent.application.AgentTeamWorkflow.Status.COMPLETED;
import static demo.multiagent.application.AgentTeamWorkflow.Status.FAILED;
import static demo.multiagent.application.AgentTeamWorkflow.Status.STARTED;
import static java.time.Duration.ofSeconds;
import static java.time.temporal.ChronoUnit.SECONDS;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.Workflow;
import demo.multiagent.domain.AgentRequest;
import demo.multiagent.domain.AgentRequest;
import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.PlanStep;
import demo.multiagent.domain.PlanStep;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.LoggerFactory;

// tag::all[]
// tag::plan[]
@ComponentId("agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> { // <1>

  public record Request(String userId, String message) {}

  // end::plan[]
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
    Status status
  ) {
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

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::plan[]

  @Override
  public WorkflowConfig configuration() {
    return WorkflowConfig.builder()
      .defaultStepTimeout(ofSeconds(30))
      .defaultStepRecovery(maxRetries(1).failoverTo(AgentTeamWorkflow::interruptStep))
      .build();
  }

  public Effect<Done> start(Request request) {
    if (currentState() == null) {
      return effects()
        .updateState(State.init(request.userId(), request.message()))
        .transitionTo(AgentTeamWorkflow::selectAgentsStep) // <3>
        .thenReply(Done.getInstance());
    } else {
      return effects()
        .error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }

  // end::plan[]

  // tag::runAgain[]
  public Effect<Done> runAgain() {
    if (currentState() != null) {
      return effects()
        .updateState(State.init(currentState().userId(), currentState().userQuery()))
        .transitionTo(AgentTeamWorkflow::selectAgentsStep) // <3>
        .thenReply(Done.getInstance());
    } else {
      return effects()
        .error("Workflow '" + commandContext().workflowId() + "' has not been started");
    }
  }

  // end::runAgain[]

  public ReadOnlyEffect<String> getAnswer() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else {
      return effects().reply(currentState().finalAnswer());
    }
  }

  // tag::plan[]
  @StepName("select-agents")
  private StepEffect selectAgentsStep() { // <2>
    var selection = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(SelectorAgent::selectAgents)
      .invoke(currentState().userQuery); // <4>

    logger.info("Selected agents: {}", selection.agents());
    if (selection.agents().isEmpty()) {
      var newState = currentState()
        .withFinalAnswer("Couldn't find any agent(s) able to respond to the original query.")
        .failed();
      return stepEffects().updateState(newState).thenEnd(); // terminate workflow
    } else {
      return stepEffects()
        .thenTransitionTo(AgentTeamWorkflow::createPlanStep)
        .withInput(selection); // <5>
    }
  }

  @StepName("create-plan")
  private StepEffect createPlanStep(AgentSelection agentSelection) { // <2>
    logger.info(
      "Calling planner with: '{}' / {}",
      currentState().userQuery,
      agentSelection.agents()
    );

    var plan = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(PlannerAgent::createPlan)
      .invoke(new PlannerAgent.Request(currentState().userQuery, agentSelection)); // <6>

    logger.info("Execution plan: {}", plan);
    return stepEffects()
      .updateState(currentState().withPlan(plan))
      .thenTransitionTo(AgentTeamWorkflow::executePlanStep); // <7>
  }

  @StepName("execute-plan")
  private StepEffect executePlanStep() { // <2>
    var stepPlan = currentState().nextStepPlan(); // <8>
    logger.info(
      "Executing plan step (agent:{}), asking {}",
      stepPlan.agentId(),
      stepPlan.query()
    );
    var agentResponse = callAgent(stepPlan.agentId(), stepPlan.query()); // <9>
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
          .thenTransitionTo(AgentTeamWorkflow::executePlanStep); // <10>
      } else {
        logger.info("No further steps to execute.");
        return stepEffects()
          .updateState(newState)
          .thenTransitionTo(AgentTeamWorkflow::summarizeStep);
      }
    }
  }

  // tag::dynamicCall[]
  private String callAgent(String agentId, String query) {
    // We know the id of the agent to call, but not the agent class.
    // Could be WeatherAgent or ActivityAgent.
    // We can still invoke the agent based on its id, given that we know that it
    // takes an AgentRequest parameter and returns String.
    var request = new AgentRequest(currentState().userId(), query);
    DynamicMethodRef<AgentRequest, String> call = componentClient
      .forAgent()
      .inSession(sessionId())
      .dynamicCall(agentId); // <9>
    return call.invoke(request);
  }

  // end::dynamicCall[]

  @StepName("summarize")
  private StepEffect summarizeStep() { // <2>
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

  // end::plan[]

  @StepName("interrupt")
  private StepEffect interruptStep() {
    logger.info("Interrupting workflow");

    return stepEffects().updateState(currentState().failed()).thenEnd();
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
  // tag::plan[]
}
// end::plan[]
// end::all[]
