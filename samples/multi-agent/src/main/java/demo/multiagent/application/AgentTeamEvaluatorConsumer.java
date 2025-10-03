package demo.multiagent.application;

import akka.javasdk.agent.evaluator.SummarizationEvaluator;
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::class[]
@ComponentId("agent-team-eval-consumer")
@Consume.FromWorkflow(AgentTeamWorkflow.class)
public class AgentTeamEvaluatorConsumer extends Consumer { // <1>
  private static final Logger logger = LoggerFactory.getLogger(AgentTeamEvaluatorConsumer.class);

  private final ComponentClient componentClient;

  public AgentTeamEvaluatorConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onStateChanged(AgentTeamWorkflow.State state) { // <2>
    if (state.status() == AgentTeamWorkflow.Status.COMPLETED) {
      evalToxicity(state);
      evalSummarization(state);
    }
    return effects().done();
  }

  private void evalToxicity(AgentTeamWorkflow.State state) {
    var result =
      componentClient
        .forAgent()
        .inSession(sessionId())
        .method(ToxicityEvaluator::evaluate) // <3>
        .invoke(state.finalAnswer());
    if (result.passed()) {
      logger.debug("Eval toxicity failed, session [{}], explanation: {}", sessionId(), result.explanation());
    } else {
      logger.warn("Eval toxicity passed, session [{}]", sessionId()); // <4>
    }
  }

  private void evalSummarization(AgentTeamWorkflow.State state) {
    var agentsAnswers = String.join("\n\n", state.agentResponses().values());

    var result =
      componentClient
          .forAgent()
          .inSession(sessionId())
          .method(SummarizationEvaluator::evaluate)
          .invoke(
              new SummarizationEvaluator.EvaluationRequest(
                  agentsAnswers,
                  state.finalAnswer()
              )
          );
    if (result.passed()) {
      logger.warn("Eval summarization failed, session [{}], explanation: {}", sessionId(), result.explanation());
    } else {
      logger.debug("Eval summarization passed, session [{}]", sessionId());
    }
  }

  private String sessionId() {
    return messageContext().eventSubject().get();
  }
}
// end::class[]
