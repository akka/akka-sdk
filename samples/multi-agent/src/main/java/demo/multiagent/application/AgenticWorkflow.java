package demo.multiagent.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import demo.multiagent.application.agents.Planner;
import demo.multiagent.application.agents.Selector;
import demo.multiagent.application.agents.Summarizer;
import demo.multiagent.application.agents.AgentsRegistry;
import demo.multiagent.domain.AgentResponse;
import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static demo.multiagent.application.AgenticWorkflow.Status.COMPLETED;
import static demo.multiagent.application.AgenticWorkflow.Status.FAILED;
import static demo.multiagent.application.AgenticWorkflow.Status.STARTED;
import static java.time.temporal.ChronoUnit.SECONDS;


@ComponentId("agentic-workflow")
public class AgenticWorkflow extends Workflow<AgenticWorkflow.State> {


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

  private final Logger logger = LoggerFactory.getLogger(AgenticWorkflow.class);

  private final AgentsRegistry agentsRegistry;
  private final Selector selector;
  private final Planner planner;
  private final Summarizer summarizer;


  public AgenticWorkflow(AgentsRegistry agentsRegistry, Selector agentSelector, Planner planner, Summarizer summarizer) {
    this.agentsRegistry = agentsRegistry;

    this.selector = agentSelector;
    this.planner = planner;
    this.summarizer = summarizer;
  }

  @Override
  public WorkflowDef<State> definition() {
    return workflow()
      .defaultStepRecoverStrategy(maxRetries(1).failoverTo(INTERRUPT))
      .defaultStepTimeout(Duration.of(30, SECONDS))
      .addStep(selectAgent())
      .addStep(planExecution())
      .addStep(runPlan()).addStep(summarize())
      .addStep(interrupt());
  }


  public Effect<Done> start(String query) {
    if (currentState() == null) {
      return effects()
        .updateState(State.init(query))
        .transitionTo(SELECT_AGENTS)
        .thenReply(Done.getInstance());
    } else {
      return effects().error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }

  public Effect<String> getAnswer() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else {
      return effects().reply(currentState().finalAnswer());
    }
  }

  private static final String SELECT_AGENTS = "select-agents";

  private Step selectAgent() {
    return step(SELECT_AGENTS)
      .call(() -> selector.selectAgents(currentState().userQuery))
      .andThen(AgentSelection.class, selection -> {
        logger.debug("Selected agents: {}", selection.agents());
          return effects().transitionTo(CREATE_PLAN_EXECUTION, selection);
        }
      );
  }

  private static final String CREATE_PLAN_EXECUTION = "create-plan-execution";

  private Step planExecution() {
    return step(CREATE_PLAN_EXECUTION)
      .call(AgentSelection.class, agentSelection -> {
        logger.debug(
            "Calling planner with: '{}' / {}",
            currentState().userQuery,
            agentSelection.agents());

          return planner.createPlan(currentState().userQuery, agentSelection);
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
            .transitionTo(EXECUTE_PLAN);
          }
        }
      );
  }

  private static final String EXECUTE_PLAN = "run-plan";

  private Step runPlan() {
    return step(EXECUTE_PLAN)
      .call(() -> {

        var stepPlan = currentState().nextStepPlan();
        logger.debug("Executing plan step (agent:{}), asking {}", stepPlan.agentId(), stepPlan.query());
        var agent = agentsRegistry.getAgent(stepPlan.agentId());

        var agentResponse = agent.query(commandContext().workflowId(), stepPlan.query());
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
            return effects().updateState(newState).transitionTo(EXECUTE_PLAN);
          } else {
            logger.debug("No further steps to execute.");
            return effects().updateState(newState).transitionTo(SUMMARIZE);
          }

        }
      );
  }

  private static final String SUMMARIZE = "summarize";

  private Step summarize() {
    return step(SUMMARIZE)
      .call(() -> {
        var agentsAnswers = currentState().agentResponses.values();
        return summarizer.summarize(currentState().userQuery, agentsAnswers);
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
      .andThen(Done.class, __ -> effects().updateState(currentState().failed()).end());
  }
}
