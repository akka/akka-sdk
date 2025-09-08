package demo.multiagent.application;

import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.ComponentId;
import demo.multiagent.domain.AgentSelection;

import java.util.List;
import java.util.Locale;

@ComponentId("planning-evaluator-agent")
public class PlanningEvaluatorAgent extends Agent {

  public record EvaluationRequest(
      String originalRequest,
      List<AgentRegistry.AgentInfo> availableAgents,
      String plan
  ) {}

  public record EvaluationResult(boolean valid, String likelihoodOfSuccess, String justification) {
    public boolean ok() {
      return valid;
    }
  }

  private static final String SYSTEM_MESSAGE =
    """
    You are an expert agent orchestrator and logician. Your task is to evaluate a generated
    [Plan] for its validity, logical soundness, and likelihood of success in achieving the
    [Original Request].
    
    You will perform your evaluation based on the following heuristics:
    1.  **Agent Validity:** Is every sub-agent called in the plan present in the [List of available sub-Agents]? Are the parameters used for each call correct and valid for that tool?
    2.  **Logical Soundness:** Is the sequence of steps logical? Does the plan correctly handle the flow of data (i.e., using the output of a previous step as input for a future step)? Are there any redundant or out-of-order steps?
    3.  **Sufficiency:** Does the plan include all the necessary steps to fully accomplish the [Original Request]? Or is a critical step missing?
    
    Your response must be a single JSON object with the following fields:
    - "valid": A boolean (`true`/`false`). Is the plan executable at all (i.e., are all tools and parameters correctly specified)?
    - "likelihoodOfSuccess": A string ("High", "Medium", or "Low"). How likely is this plan to fully satisfy the user's request?
    - "justification": A brief, high-level summary of your reasoning.
    """.stripIndent();

  private static final String USER_MESSAGE =
    """
    [Original Request]
    %s
    
    [List of available sub-Agents]
    %s

    [Plan]
    %s
    """.stripIndent();


  public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
    var agents = JsonSupport.encodeToString(request.availableAgents);

    String evaluationPrompt = USER_MESSAGE.formatted(request.originalRequest, agents, request.plan);

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(evaluationPrompt)
      .responseConformsTo(EvaluationResult.class)
      .thenReply();
  }

}
