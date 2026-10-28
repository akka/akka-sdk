package demo.multiagent.application;

// tag::all[]
import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.LedgerClient;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Judges the answer of a completed activity task against the task instructions and the user's
 * preferences, with the {@link EvaluatorAgent} as the judge.
 */
@Component(id = "activity-evaluator") // <1>
public class ActivityEvaluator extends Evaluator {

  private static final Logger logger = LoggerFactory.getLogger(ActivityEvaluator.class);

  private final ComponentClient componentClient;
  private final LedgerClient ledger;

  public ActivityEvaluator(ComponentClient componentClient, LedgerClient ledger) {
    this.componentClient = componentClient;
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = ledger.getInteraction(context.subject().interactionId()); // <2>
    if (
      interaction.taskContext().isEmpty() || interaction.toolCalls("complete_task").isEmpty()
    ) {
      return effects()
        .notApplicable("the coordinator did not complete the task in this interaction"); // <3>
    }

    var taskId = interaction.taskContext().get().taskId();
    var task = componentClient.forTask(taskId).get(ActivityTasks.SUGGEST_ACTIVITIES); // <4>
    if (task.status() != TaskStatus.COMPLETED || task.result().isEmpty()) {
      return effects()
        .inconclusive("task [" + taskId + "] is " + task.status() + " without a result");
    }

    var userId = task
      .instructions()
      .lines()
      .findFirst()
      .orElse("")
      .replace("User:", "")
      .trim();
    var preferences = componentClient
      .forEventSourcedEntity(userId)
      .method(PreferencesEntity::getPreferences)
      .invoke(); // <5>

    var judgement = componentClient
      .forAgent()
      .inSession(context.evaluationId()) // <6>
      .method(EvaluatorAgent::evaluate)
      .invoke(
        new EvaluatorAgent.EvaluationRequest(
          task.instructions(),
          preferences.entries(),
          task.result().get()
        )
      );

    logger.info(
      "Judge says [{}] for task [{}]: {}",
      judgement.label(),
      taskId,
      judgement.explanation()
    );
    return switch (judgement.label().toLowerCase(Locale.ROOT)) { // <7>
      case "correct" -> effects().complete(verdict(true, judgement, taskId));
      case "incorrect" -> effects().complete(verdict(false, judgement, taskId));
      default -> effects().inconclusive("unknown judge label [" + judgement.label() + "]");
    };
  }

  private static Evaluation verdict(
    boolean passed,
    EvaluatorAgent.Result judgement,
    String taskId
  ) {
    return Evaluation.of(passed, judgement.explanation())
      .withLabel(judgement.label())
      .withAttribute("taskId", taskId);
  }
}
// end::all[]
