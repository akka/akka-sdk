package demo.multiagent.application;

import static demo.multiagent.application.AgentTeamWorkflow.Status.*;

import demo.multiagent.domain.AgentRequest;
import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.PlanStep;

// tag::all[]
import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static java.time.temporal.ChronoUnit.SECONDS;

// tag::plan[]
@ComponentId("agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> { // <1>
  public record Request(String userId, String message) {
  }

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

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::plan[]
  @Override
  public WorkflowDef<State> definition() {
    return workflow()
        .defaultStepRecoverStrategy(maxRetries(1).failoverTo(INTERRUPT))
        .defaultStepTimeout(Duration.of(30, SECONDS))
        .addStep(selectAgentsStep()) // <2>
        .addStep(planStep())
        .addStep(executePlanStep())
        .addStep(summarizeStep())
        .addStep(interruptStep());
  }

  public Effect<Done> start(Request request) {
    if (currentState() == null) {
      return effects()
          .updateState(State.init(request.userId(), request.message()))
          .transitionTo(SELECT_AGENTS) // <3>
          .thenReply(Done.getInstance());
    } else {
      return effects().error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }
  // end::plan[]

  // tag::runAgain[]
  public Effect<Done> runAgain() {
    if (currentState() != null) {
      return effects()
          .updateState(State.init(currentState().userId(), currentState().userQuery()))
          .transitionTo(SELECT_AGENTS) // <3>
          .thenReply(Done.getInstance());
    } else {
      return effects().error("Workflow '" + commandContext().workflowId() + "' has not been started");
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
  private static final String SELECT_AGENTS = "select-agents";

  private Step selectAgentsStep() {
    return step(SELECT_AGENTS)
        .call(() ->
            componentClient.forAgent().inSession(sessionId()).method(SelectorAgent::selectAgents)
                .invoke(currentState().userQuery)) // <4>
        .andThen(AgentSelection.class, selection -> {
              logger.info("Selected agents: {}", selection.agents());
              if (selection.agents().isEmpty()) {
                var newState = currentState()
                    .withFinalAnswer("Couldn't find any agent(s) able to respond to the original query.")
                    .failed();
                return effects().updateState(newState).end(); // terminate workflow
              } else {
                return effects().transitionTo(CREATE_PLAN, selection); // <5>

              }
            }
        );
  }

  private static final String CREATE_PLAN = "create-plan";

  private Step planStep() {
    return step(CREATE_PLAN)
        .call(AgentSelection.class, agentSelection -> {
              logger.info(
                  "Calling planner with: '{}' / {}",
                  currentState().userQuery,
                  agentSelection.agents());

              return componentClient.forAgent().inSession(sessionId()).method(PlannerAgent::createPlan)
                  .invoke(new PlannerAgent.Request(currentState().userQuery, agentSelection)); // <6>
            }
        )
        .andThen(Plan.class, plan -> {
              logger.info("Execution plan: {}", plan);
              return effects()
                  .updateState(currentState().withPlan(plan))
                  .transitionTo(EXECUTE_PLAN); // <7>
            }
        );
  }

  private static final String EXECUTE_PLAN = "execute-plan";

  private Step executePlanStep() {
    return step(EXECUTE_PLAN)
        .call(() -> {
          var stepPlan = currentState().nextStepPlan(); // <8>
          logger.info("Executing plan step (agent:{}), asking {}", stepPlan.agentId(), stepPlan.query());
          var agentResponse = callAgent(stepPlan.agentId(), stepPlan.query()); // <9>
          if (agentResponse.startsWith("ERROR")) {
            throw new RuntimeException("Agent '" + stepPlan.agentId() + "' responded with error: " + agentResponse);
          } else {
            logger.info("Response from [agent:{}]: '{}'", stepPlan.agentId(), agentResponse);
            return agentResponse;
          }

        })
        .andThen(String.class, answer -> {
              var newState = currentState().addAgentResponse(answer);

              if (newState.hasMoreSteps()) {
                logger.info("Still {} steps to execute.", newState.plan().steps().size());
                return effects().updateState(newState).transitionTo(EXECUTE_PLAN); // <10>
              } else {
                logger.info("No further steps to execute.");
                return effects().updateState(newState).transitionTo(SUMMARIZE);
              }

            }
        );
  }

  // tag::dynamicCall[]
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
            .dynamicCall(agentId); // <9>
    return call.invoke(request);
  }
  // end::dynamicCall[]
  // end::plan[]

  private static final String SUMMARIZE = "summarize";

  private Step summarizeStep() {
    return step(SUMMARIZE)
        .call(() -> {
          var agentsAnswers = currentState().agentResponses.values();
          return componentClient.forAgent().inSession(sessionId()).method(SummarizerAgent::summarize)
              .invoke(new SummarizerAgent.Request(currentState().userQuery, agentsAnswers));
        })
        .andThen(String.class, finalAnswer ->
            effects().updateState(currentState().withFinalAnswer(finalAnswer).complete()).pause());
  }

  private static final String INTERRUPT = "interrupt";

  private Workflow.Step interruptStep() {
    return step(INTERRUPT)
        .call(() -> {
          logger.info("Interrupting workflow");
          return Done.getInstance();
        })
        .andThen(() -> effects().updateState(currentState().failed()).end());
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
  // tag::plan[]
}
// end::plan[]
// end::all[]
