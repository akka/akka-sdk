package com.example.eval;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.eval.EvalCase;
import akka.javasdk.testkit.eval.EvalCases;
import akka.javasdk.testkit.eval.ExperimentRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The evaluation of {@link OrderAgent} with the models mocked, so it runs without an API key. The
 * mocks play a fixed set of model turns, which makes the cases deterministic: they check that the
 * agent, the tools and the evaluators fit together. {@link OrderAgentQualityIntegrationTest} runs
 * the same cases against a real model.
 */
public class OrderAgentEvalIntegrationTest extends TestKitSupport {

  private final StubOrderService orders = new StubOrderService();
  private final TestModelProvider orderModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  // tag::mocked[]
  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withDependencyProvider(dependencies())
      .withModelProvider(OrderAgent.class, orderModel) // <1>
      .withModelProvider(OrderJudge.class, judgeModel)
      .withAdditionalConfig(
        """
        akka.javasdk.evaluation.evaluators {
          order-lookup.agents.order-agent { trigger = interaction, trigger-on-failure = true }
          refund-policy.agents.order-agent { trigger = interaction }
          reply-grounding.agents.order-agent { trigger = interaction }
          judge {
            criterion = "The reply answers the question with what the tools returned."
            threshold = 0.7
            agents.order-agent { trigger = interaction }
          }
        }
        """
      );
  }

  @BeforeEach
  public void theModels() {
    MockedModels.scriptOrderAgent(orderModel); // <2>
    MockedModels.scriptJudge(judgeModel);
  }

  // end::mocked[]

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
    orders.reset();
    orders.addOrder(new Order("o_42", "shipped", 2599, "leave it with a neighbour"));
    orders.addOrder(new Order("o_9", "delivered", 4999, ""));
  }

  @Test
  public void everyBoundEvaluatorReportsOnTheInteraction() {
    var report = new ExperimentRunner(testKit)
      .cases(OrderCases.CURATED)
      .agent(OrderAgent::ask)
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // tag::cases-file[]
  @Test
  public void casesFromAFile() {
    var cases = EvalCases.readJsonLinesResource("eval/cases.jsonl", String.class); // <1>

    var report = new ExperimentRunner(testKit).cases(cases).agent(OrderAgent::ask).run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.results()).hasSize(3);
  }

  // end::cases-file[]

  // tag::judge[]
  @Test
  public void theJudgeScoresEveryReply() {
    var report = new ExperimentRunner(testKit)
      .cases(OrderCases.FULL_REFUND)
      .agent(OrderAgent::ask)
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.results().getFirst().describe()).contains(
      "PASS judge: answers from what the tools returned (score 0.90)"
    ); // <1>
  }

  // end::judge[]

  // tag::failed-case[]
  @Test
  public void aReplyWithoutALookupFailsTheCase() {
    orderModel.reset();
    orderModel.whenUserMessage(message -> true).reply("Order o_42 was delivered yesterday."); // <1>

    var report = new ExperimentRunner(testKit)
      .cases(OrderCases.ORDER_STATUS)
      .agent(OrderAgent::ask)
      .run();

    assertThat(report.passed()).isFalse();
    assertThat(report.render())
      .contains("case order-status run 1 FAILED")
      .contains("FAIL order-lookup: no getOrder call for a message that names an order") // <2>
      .contains("NOT_APPLICABLE reply-grounding: no order was looked up"); // <3>
  }

  // end::failed-case[]

  // tag::target-failure[]
  @Test
  public void anUnknownOrderIsATargetFailure() {
    var unknown = EvalCase.of("unknown-order", "Where is order o_404?");

    var report = new ExperimentRunner(testKit).cases(unknown).agent(OrderAgent::ask).run();

    assertThat(report.passed()).isFalse();
    var result = report.results().getFirst();
    assertThat(result.describe()).contains("FAIL " + ExperimentRunner.TARGET); // <1>
    assertThat(result.evidence().failed()).isTrue(); // <2>
    assertThat(result.evidence().toolCalls().getFirst().name()).isEqualTo("getOrder");
    assertThat(result.describe()).contains("PASS order-lookup"); // <3>
  }
  // end::target-failure[]
}
