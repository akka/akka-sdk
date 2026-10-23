package demo.multiagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

// tag::all[]
public class ActivityEvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();
  private final AtomicReference<String> judgedRequest = new AtomicReference<>();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ActivityCoordinator.class, coordinatorModel)
      .withModelProvider(ActivityAgent.class, activitiesModel)
      .withModelProvider(WeatherAgent.class, weatherModel)
      .withModelProvider(EvaluatorAgent.class, judgeModel); // <1>
  }

  @Test
  public void shouldJudgeTheAnswerOfACompletedTask() {
    CoordinatorModels.scriptStockholm(coordinatorModel, weatherModel, activitiesModel);
    judgeModel
      .whenMessage(msg -> {
        judgedRequest.set(msg);
        return true;
      })
      .reply(
        """
        {
          "label": "Correct",
          "explanation": "The suggestion is appropriate for the user."
        }
        """
      ); // <2>

    var instructions =
      "User: alice\n\nI am in Stockholm. What should I do? Beware of the weather";
    var coordinatorId = UUID.randomUUID().toString();
    var taskId = componentClient
      .forAutonomousAgent(ActivityCoordinator.class, coordinatorId)
      .runSingleTask(ActivityTasks.SUGGEST_ACTIVITIES.instructions(instructions));

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var task = componentClient.forTask(taskId).get(ActivityTasks.SUGGEST_ACTIVITIES);
        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
      });

    var ledger = testKit.getLedgerClient();
    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var interactions = ledger.getInteractions("activity-coordinator", coordinatorId); // <3>
        var completing = interactions
          .stream()
          .filter(interaction -> !interaction.toolCalls("complete_task").isEmpty())
          .findFirst()
          .orElseThrow();
        var verdict = evaluationOf(completing);
        assertThat(verdict.evaluation().passed()).isTrue(); // <4>
        assertThat(verdict.evaluation().label()).contains("Correct");

        var delegating = interactions
          .stream()
          .filter(interaction -> interaction.toolCalls("complete_task").isEmpty())
          .findFirst()
          .orElseThrow();
        assertThat(outcomeOf(delegating)).isInstanceOf(
          EvaluationRecord.Outcome.NotApplicable.class
        ); // <5>
      });

    assertThat(judgedRequest.get()).contains(instructions); // <6>
    assertThat(judgedRequest.get()).contains(CoordinatorModels.ANSWER);
  }

  private EvaluationRecord.Outcome outcomeOf(InteractionRecord interaction) {
    var evaluations = testKit.getLedgerClient().getEvaluations(interaction.interactionId());
    assertThat(evaluations).hasSize(1);
    return evaluations.getFirst().outcome();
  }

  private EvaluationRecord.Outcome.Verdict evaluationOf(InteractionRecord interaction) {
    assertThat(outcomeOf(interaction)).isInstanceOf(EvaluationRecord.Outcome.Verdict.class);
    return (EvaluationRecord.Outcome.Verdict) outcomeOf(interaction);
  }
}
// end::all[]
