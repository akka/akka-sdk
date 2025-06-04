package demo.multiagent.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.Workflow;
import demo.multiagent.application.agents.Planner;
import demo.multiagent.application.agents.Selector;
import demo.multiagent.application.agents.Summarizer;
import demo.multiagent.domain.AgentResponse;
import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static demo.multiagent.application.AgentTeam.Status.*;
import static java.time.temporal.ChronoUnit.SECONDS;

// tag::plan[]
@ComponentId("agent-team")
public class AgentTeam extends Workflow<AgentTeam.State> { // <1>
  // end::plan[]

  enum Status {
    STARTED,
    COMPLETED,
    FAILED,
  }

  public record State(String userQuery,
                      Plan plan,
                      String finalAnswer,
                      Map<String, AgentResponse> agentResponses,
                      Status status) {

    public static State init(String query) {
      return new State(query, new Plan(), "", new HashMap<>(), STARTED);
    }


    public State withFinalAnswer(String answer) {
      return new State(userQuery, plan, answer, agentResponses, status);
    }

    public State addAgentResponse(AgentResponse response) {
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
      return new State(userQuery, plan, finalAnswer, agentResponses, STARTED);
    }

    public State complete() {
      return new State(userQuery, plan, finalAnswer, agentResponses, COMPLETED);
    }

    public State failed() {
      return new State(userQuery, plan, finalAnswer, agentResponses, FAILED);
    }

  }

  private static final Logger logger = LoggerFactory.getLogger(AgentTeam.class);

  private final ComponentClient componentClient;

  public AgentTeam(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::plan[]
  @Override
  public WorkflowDef<State> definition() {
    return workflow()
      .defaultStepRecoverStrategy(maxRetries(1).failoverTo(INTERRUPT))
      .defaultStepTimeout(Duration.of(30, SECONDS))
      .addStep(selectAgent()) // <2>
      .addStep(plan())
      .addStep(executePlan())
      .addStep(summarize())
      .addStep(interrupt());
  }

  public Effect<Done> start(String query) {
    if (currentState() == null) {
      return effects()
        .updateState(State.init(query))
        .transitionTo(SELECT_AGENTS) // <3>
        .thenReply(Done.getInstance());
    } else {
      return effects().error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }
  // end::plan[]

  public Effect<String> getAnswer() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else {
      return effects().reply(currentState().finalAnswer());
    }
  }

  // tag::plan[]
  private static final String SELECT_AGENTS = "select-agents";

  private Step selectAgent() {
    return step(SELECT_AGENTS)
      .call(() ->
          componentClient.forAgent().inSession(sessionId()).method(Selector::selectAgents)
              .invoke(currentState().userQuery)) // <4>
      .andThen(AgentSelection.class, selection -> {
        logger.debug("Selected agents: {}", selection.agents());
          return effects().transitionTo(CREATE_PLAN, selection); // <5>
        }
      );
  }

  private static final String CREATE_PLAN = "create-plan";

  private Step plan() {
    return step(CREATE_PLAN)
      .call(AgentSelection.class, agentSelection -> {
        logger.debug(
            "Calling planner with: '{}' / {}",
            currentState().userQuery,
            agentSelection.agents());

          return componentClient.forAgent().inSession(sessionId()).method(Planner::createPlan)
              .invoke(new Planner.Request(currentState().userQuery, agentSelection)); // <6>
        }
      )
      .andThen(Plan.class, plan -> {
        logger.debug("Execution plan: {}", plan);
        if (plan.steps().isEmpty()) {

          var newState = currentState()
            .withFinalAnswer("Couldn't find any agent(s) able to respond to the original query.")
            .failed();
          return effects().updateState(newState).end(); // terminate workflow

        } else {
          return effects()
            .updateState(currentState().withPlan(plan))
            .transitionTo(EXECUTE_PLAN); // <7>
          }
        }
      );
  }

  private static final String EXECUTE_PLAN = "execute-plan";

  private Step executePlan() {
    return step(EXECUTE_PLAN)
      .call(() -> {
        var stepPlan = currentState().nextStepPlan(); // <8>
        logger.debug("Executing plan step (agent:{}), asking {}", stepPlan.agentId(), stepPlan.query());
        var agentResponse = callAgent(stepPlan.agentId(), stepPlan.query()); // <9>
        if (agentResponse.isValid()) {
          logger.debug("Response from [agent:{}]: '{}'", stepPlan.agentId(), agentResponse);
          return agentResponse;
        } else {
          throw new RuntimeException("Agent '" + stepPlan.agentId() + "' responded with error: " + agentResponse.error());
        }

      })
      .andThen(AgentResponse.class, answer -> {
          var newState = currentState().addAgentResponse(answer);

          if (newState.hasMoreSteps()) {
            logger.debug("Still {} steps to execute.", newState.plan().steps().size());
            return effects().updateState(newState).transitionTo(EXECUTE_PLAN); // <10>
          } else {
            logger.debug("No further steps to execute.");
            return effects().updateState(newState).transitionTo(SUMMARIZE);
          }

        }
      );
  }

  // tag::dynamicCall[]
  private AgentResponse callAgent(String agentId, String query) {
    // We know the id of the agent to call, but not the agent class.
    // Could be WeatherAgent or ActivityAgent.
    // We can still invoke the agent based on its id, given that we know that it
    // takes a String parameter and returns AgentResponse.
    DynamicMethodRef<String, AgentResponse> call =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .dynamicCall(agentId); // <9>
    return call.invoke(query);
  }
  // end::dynamicCall[]
  // end::plan[]

  private static final String SUMMARIZE = "summarize";

  private Step summarize() {
    return step(SUMMARIZE)
      .call(() -> {
        var agentsAnswers = currentState().agentResponses.values();
        return componentClient.forAgent().inSession(sessionId()).method(Summarizer::summarize)
                .invoke(new Summarizer.Request(currentState().userQuery, agentsAnswers));
      })
      .andThen(String.class, finalAnswer ->
        effects().updateState(currentState().withFinalAnswer(finalAnswer).complete()).end());
  }

  private static final String INTERRUPT = "interrupt";

  private Workflow.Step interrupt() {
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
  // tag::plan[]
}
// end::plan[]
