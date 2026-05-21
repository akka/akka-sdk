package demo.multiagent.application;

// tag::all[]
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskEvent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "evaluation-consumer")
@Consume.FromEventSourcedEntity(TaskEntity.class) // <1>
public class EvaluationConsumer extends Consumer {

  private static final Logger logger = LoggerFactory.getLogger(EvaluationConsumer.class);

  private final ComponentClient componentClient;

  public EvaluationConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(TaskEvent event) {
    if (event instanceof TaskEvent.TaskCompleted completed &&
        ActivityTasks.SUGGEST_ACTIVITIES.name().equals(completed.name())) { // <2>
        var taskId = completed.taskId();
        var snapshot = componentClient.forTask(taskId).get(ActivityTasks.SUGGEST_ACTIVITIES); // <3>

        // Custom LLM-as-judge: compare answer against the original (preference-aware) request
        var judgement = componentClient
            .forAgent()
            .inSession(taskId)
            .method(EvaluatorAgent::evaluate)
            .invoke(
                new EvaluatorAgent.EvaluationRequest(
                    snapshot.instructions(),
                    snapshot.result().orElse("")
                )
            ); // <4>
        if (judgement.passed()) {
          logger.debug("LLM judge passed for task [{}]", taskId);
        } else {
          logger.warn(
              "LLM judge failed for task [{}], explanation: {}",
              taskId,
              judgement.explanation()
          );
        }

        // Built-in toxicity evaluator on the final answer
        var toxicity = componentClient
            .forAgent()
            .inSession(taskId)
            .method(ToxicityEvaluator::evaluate)
            .invoke(snapshot.result().orElse("")); // <5>
        if (toxicity.passed()) {
          logger.debug("Toxicity check passed for task [{}]", taskId);
        } else {
          logger.warn(
              "Toxicity check failed for task [{}], explanation: {}",
              taskId,
              toxicity.explanation()
          );
        }
    }
    return effects().done();
  }
}
// end::all[]
