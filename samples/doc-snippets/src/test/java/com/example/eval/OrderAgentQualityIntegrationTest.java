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
import org.junit.jupiter.api.AfterEach;
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

  @AfterEach
  public void reset() {
    orders.reset(); // <4>
  }

  // end::class[]

  // tag::status-case[]
  private EvalCase orderStatus() {
    return new EvalCase(
      "order-status", // <1>
      "Where is order o_42?", // <2>
      () -> orders.addOrder(new Order("o_42", "shipped", 2599)), // <3>
      Evaluators.tools("getOrder"), // <4>
      Evaluators.toolArgument("getOrder", "orderId", "o_42"),
      Evaluators.forbiddenTools("issueRefund"),
      Evaluators.answerContains("shipped")
    );
  }

  // end::status-case[]
  // tag::refund-case[]
  private EvalCase fullRefund() {
    return new EvalCase(
      "full-refund",
      "Order o_9 arrived broken. I want my money back.",
      () -> orders.addOrder(new Order("o_9", "delivered", 4999)),
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
          .and(Gate.evaluatorRateAtLeast(Evaluators.TOOL_ARGUMENTS, 1.0)) // <2>
          .and(Gate.noTargetFailures()) // <3>
      )
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::batch[]

  // tag::case-evaluator[]
  @Test
  public void aRefundNeverExceedsTheOrderTotal() {
    var order = new Order("o_9", "delivered", 4999);

    var refund = new EvalCase(
      "refund-within-total",
      "Order o_9 arrived broken. I want my money back.",
      () -> orders.addOrder(order),
      Evaluators.tools("issueRefund"),
      new RefundWithinTotal(order.totalCents()) // <1>
    );

    var report = new ExperimentRunner(testKit).cases(refund).agent(OrderAgent::ask).run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::case-evaluator[]

  // tag::judge[]
  @Test
  public void theReplyApologisesAndStatesTheAmount() {
    var judge = Judge.agent(testKit); // <1>

    var refund = new EvalCase(
      "judged-refund",
      "Order o_9 arrived broken. I want my money back.",
      () -> orders.addOrder(new Order("o_9", "delivered", 4999)),
      Evaluators.tools("issueRefund"), judge.mustSatisfy("the reply apologises and states the refunded amount") // <2>
    );

    var report = new ExperimentRunner(testKit).cases(refund).agent(OrderAgent::ask).run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.cases().getFirst().describe()).contains("judge:"); // <3>
  }

  // end::judge[]

  // tag::judged-batch[]
  @Test
  public void repliesStayFactual() {
    var judge = Judge.agent(testKit); // <1>
    var factual = judge.scoringAtLeast("the reply states only what the tools returned", 0.7); // <2>

    var cases = curated().stream().map(evalCase -> evalCase.withEvaluators(factual)).toList();

    var report = new ExperimentRunner(testKit)
      .cases(cases)
      .agent(OrderAgent::ask)
      .gate(Gate.evaluatorRateAtLeast(Evaluators.JUDGE, 0.8)) // <3>
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::judged-batch[]

  // tag::scripted-judge[]
  @Test
  public void aJudgeCanBeAnyFunction() {
    Judge shortReply = question -> // <1>
      Judge.Verdict.of(
        question.reply().length() <= 200 ? 1.0 : 0.0,
        "the reply has " + question.reply().length() + " characters"
      );

    var status = new EvalCase(
      "short-status",
      "Where is order o_42?",
      () -> orders.addOrder(new Order("o_42", "shipped", 2599)),
      shortReply.mustSatisfy("the reply is short") // <2>
    );

    var report = new ExperimentRunner(testKit).cases(status).agent(OrderAgent::ask).run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::scripted-judge[]

  // tag::failed-report[]
  @Test
  public void aFailedCaseShowsWhatTheAgentDid() {
    var wrongOrder = new EvalCase(
      "wrong-order",
      "Where is order o_42?",
      () -> orders.addOrder(new Order("o_42", "shipped", 2599)),
      Evaluators.toolArgument("getOrder", "orderId", "o_43")
    );

    var report = new ExperimentRunner(testKit).cases(wrongOrder).agent(OrderAgent::ask).run();

    assertThat(report.passed()).isFalse();
    assertThat(report.render())
      .contains("case wrong-order FAILED")
      .contains("getOrder{orderId=o_42}")
      .contains("expected o_43");
  }

  // end::failed-report[]

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
    var result = report.cases().getFirst();
    assertThat(result.describe()).contains("FAIL " + Evaluators.TARGET); // <1>
    assertThat(result.interaction().toolCalls().getFirst().error()).isPresent(); // <2>
  }

  // end::target-failure[]

  // tag::replay[]
  @Test
  public void replayedTrafficStillHolds() {
    var bindings = ToolBindings.builder() // <1>
      .bind("getOrder", orders::loadOrder)
      .bind("issueRefund", orders::loadRefund)
      .build();
    var replayed = EvalCaseParser.parse(captures(), bindings); // <2>

    var report = new ExperimentRunner(testKit)
      .cases(replayed)
      .agent(OrderAgent::ask)
      .gate(Gate.passRateAtLeast(0.9)) // <3>
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  private static Path captures() {
    try {
      return Path.of(
        OrderAgentQualityIntegrationTest.class.getResource("/eval/captures.jsonl").toURI()
      );
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
  // end::replay[]
}
