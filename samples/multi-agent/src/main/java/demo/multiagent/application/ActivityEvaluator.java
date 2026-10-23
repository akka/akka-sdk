package demo.multiagent.application;

// tag::all[]
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.LedgerClient;

/**
 * Judges the answer of a completed activity task against the task instructions, with the {@link
 * EvaluatorAgent} as the LLM judge.
 */
@Component(id = "activity-evaluator")
public class ActivityEvaluator extends Evaluator { // <1>

  /** The arguments of the {@code complete_task} call: the result of a task with a String result. */
  public record TaskResult(String result) {}

  private final ComponentClient componentClient;
  private final LedgerClient ledger;

  public ActivityEvaluator(ComponentClient componentClient, LedgerClient ledger) {
    this.componentClient = componentClient;
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = ledger.getInteraction(context.subject().interactionId()); // <2>
    var completion = interaction.toolCalls("complete_task").stream().findFirst();
    if (interaction.taskContext().isEmpty() || completion.isEmpty()) {
      return effects()
        .notApplicable("the coordinator did not complete the task in this interaction"); // <3>
    }

    var taskId = interaction.taskContext().get().taskId();
    var task = componentClient.forTask(taskId).get(ActivityTasks.SUGGEST_ACTIVITIES); // <4>
    var answer = completion.get().argumentsAs(TaskResult.class).result(); // <5>

    var judgement = componentClient
      .forAgent()
      .inSession(context.evaluationId())
      .method(EvaluatorAgent::evaluate)
      .invoke(new EvaluatorAgent.EvaluationRequest(task.instructions(), answer)); // <6>

    return effects()
      .complete(
        Evaluation.of(judgement.passed(), judgement.explanation()).withLabel(
          judgement.label()
        )
      ); // <7>
  }
}
// end::all[]
