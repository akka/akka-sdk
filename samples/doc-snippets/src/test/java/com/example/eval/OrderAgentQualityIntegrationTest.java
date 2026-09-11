package com.example.eval;

import static org.assertj.core.api.Assertions.assertThat;

// tag::imports[]
import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.eval.EvalCase;
import akka.javasdk.testkit.eval.EvalCaseParser;
import akka.javasdk.testkit.eval.Evaluators;
import akka.javasdk.testkit.eval.ExperimentRunner;
import akka.javasdk.testkit.eval.Gate;
import akka.javasdk.testkit.eval.Judge;
import akka.javasdk.testkit.eval.ToolBindings;
// end::imports[]
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The evaluation of {@link OrderAgent} against the model configured in application.conf. Skipped
 * unless the build runs with {@code -Deval=true} and the model's API key in the environment.
 */
// tag::class[]
@EnabledIfSystemProperty(named = "eval", matches = "true") // <1>
public class OrderAgentQualityIntegrationTest extends TestKitSupport {

  private final StubOrderService orders = new StubOrderService(); // <2>

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withDependencyProvider(dependencies()); // <3>
  }

  private DependencyProvider dependencies() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> type) {
        if (type == OrderService.class) return (T) orders;
        throw new IllegalArgumentException("no such dependency: " + type);
      }
    };
  }

  @BeforeEach
  public void theOrders() {
    orders.reset(); // <4>
    orders.addOrder(new Order("o_42", "shipped", 2599));
    orders.addOrder(new Order("o_9", "delivered", 4999));
  }

  // end::class[]

  // tag::status-case[]
  private EvalCase orderStatus() {
    return EvalCase.of(
      "order-status", // <1>
      "Where is order o_42?", // <2>
      Evaluators.tools("getOrder"), // <3>
      Evaluators.toolArgument("getOrder", "orderId", "o_42"),
      Evaluators.forbiddenTools("issueRefund"),
      Evaluators.answerContains("shipped")
    );
  }

  // end::status-case[]
  // tag::refund-case[]
  private EvalCase fullRefund() {
    return EvalCase.of(
      "full-refund",
      "Order o_9 arrived broken. I want my money back.",
      Evaluators.toolOrder("getOrder", "issueRefund"), // <1>
      Evaluators.toolArgument("issueRefund", "amountCents", 4999), // <2>
      Evaluators.answerMatches("refund(ed)?.*49\\.99"), // <3>
      Evaluators.toolCallsAtMost(2), // <4>
      Evaluators.modelCallsAtMost(3)
    );
  }

  // end::refund-case[]
  private EvalCase greeting() {
    return EvalCase.of(
      "greeting",
      "hello?",
      Evaluators.forbiddenTools("getOrder", "issueRefund"),
      Evaluators.answerContains("order number")
    );
  }

  // tag::curated[]
  private List<EvalCase> curated() {
    return List.of(orderStatus(), fullRefund(), greeting());
  }

  // end::curated[]

  // tag::first-case[]
  @Test
  public void looksTheOrderUpBeforeAnswering() {
    var report = new ExperimentRunner(testKit)
      .cases(orderStatus()) // <1>
      .agent(OrderAgent::ask) // <2>
      .run(); // <3>

    assertThat(report.passed()).withFailMessage(report::render).isTrue(); // <4>
  }

  // end::first-case[]

  // tag::batch[]
  @Test
  public void qualityGate() {
    var report = new ExperimentRunner(testKit)
      .cases(curated())
      .agent(OrderAgent::ask)
      .gate(
        Gate.passRateAtLeast(0.9) // <1>
          .and(Gate.evaluatorRateAtLeast(Evaluators.ToolArgument.class, 1.0)) // <2>
          .and(Gate.noTargetFailures()) // <3>
      )
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::batch[]

  // tag::case-evaluator[]
  @Test
  public void aRefundNeverExceedsTheOrderTotal() {
    var refund = EvalCase.of(
      "refund-within-total",
      "Order o_9 arrived broken. I want my money back.",
      Evaluators.tools("issueRefund"),
      new RefundWithinTotal(orders.getOrder("o_9").totalCents()) // <1>
    );

    var report = new ExperimentRunner(testKit).cases(refund).agent(OrderAgent::ask).run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::case-evaluator[]

  // tag::judge[]
  @Test
  public void theReplyApologizesAndStatesTheAmount() {
    var judge = Judge.modelBased(testKit); // <1>

    var refund = EvalCase.of(
      "judged-refund",
      "Order o_9 arrived broken. I want my money back.",
      Evaluators.tools("issueRefund"),
      judge.mustSatisfy("the reply apologizes and states the refunded amount") // <2>
    );

    var report = new ExperimentRunner(testKit).cases(refund).agent(OrderAgent::ask).run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.results().getFirst().describe()).contains("judge:"); // <3>
  }

  // end::judge[]

  // tag::judged-batch[]
  @Test
  public void repliesStayFactual() {
    var judge = Judge.modelBased(testKit);
    var factual = judge.scoringAtLeast("the reply states only what the tools returned", 0.7); // <1>

    var report = new ExperimentRunner(testKit)
      .cases(curated())
      .evaluator(factual)
      .agent(OrderAgent::ask)
      .gate(Gate.evaluatorRateAtLeast(Evaluators.JudgeEvaluator.class, 0.8)) // <2>
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::judged-batch[]

  @Test
  public void aFailedCaseShowsWhatTheAgentDid() {
    var wrongOrder = EvalCase.of(
      "wrong-order",
      "Where is order o_42?",
      Evaluators.toolArgument("getOrder", "orderId", "o_43")
    );

    var report = new ExperimentRunner(testKit).cases(wrongOrder).agent(OrderAgent::ask).run();

    assertThat(report.passed()).isFalse();
    assertThat(report.render())
      .contains("case wrong-order FAILED")
      .contains("getOrder{orderId=o_42}")
      .contains("expected o_43");
  }

  // tag::target-failure[]
  @Test
  public void anUnknownOrderIsATargetFailure() {
    var unknown = EvalCase.of(
      "unknown-order",
      "Where is order o_404?",
      Evaluators.tools("getOrder")
    );

    var report = new ExperimentRunner(testKit).cases(unknown).agent(OrderAgent::ask).run();

    assertThat(report.passed()).isFalse();
    var result = report.results().getFirst();
    assertThat(result.describe()).contains("FAIL " + Evaluators.TARGET); // <1>
    assertThat(result.interaction().toolCalls().getFirst().error()).isPresent(); // <2>
  }

  // end::target-failure[]

  // tag::replay-replies[]
  @Test
  public void recordedRepliesStayOnTopic() {
    var replayed = EvalCaseParser.parse(recording("/eval/replies.jsonl")); // <1>
    var judge = Judge.modelBased(testKit);
    var onTopic = judge.scoringAtLeast(
      "the reply asks for an order number or closes the conversation",
      0.7
    );

    var report = new ExperimentRunner(testKit)
      .cases(replayed)
      .evaluator(Evaluators.forbiddenTools("issueRefund")) // <2>
      .evaluator(onTopic) // <3>
      .agent(OrderAgent::ask)
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  private static Path recording(String resource) {
    try {
      return Path.of(OrderAgentQualityIntegrationTest.class.getResource(resource).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  // end::replay-replies[]

  // tag::replay[]
  @Test
  public void replayedTrafficStillHolds() {
    var replayed = EvalCaseParser.parse(recording("/eval/captures.jsonl")); // <1>
    var bindings = ToolBindings.builder() // <2>
      .bind("getOrder", orders::loadOrder)
      .bind("issueRefund", orders::loadRefund)
      .build();

    var report = new ExperimentRunner(testKit)
      .cases(replayed)
      .bindings(bindings) // <3>
      .agent(OrderAgent::ask)
      .gate(Gate.passRateAtLeast(0.9)) // <4>
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }
  // end::replay[]
}
