package com.example.eval;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.eval.ExperimentRunner;
import akka.javasdk.testkit.eval.Gate;
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
    return TestKit.Settings.DEFAULT.withDependencyProvider( // <3>
      dependencies()
    ).withAdditionalConfig( // <4>
      // tag::bindings[]
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
      // end::bindings[]
    );
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
    orders.reset(); // <5>
    orders.addOrder(new Order("o_42", "shipped", 2599, "leave it with a neighbour"));
    orders.addOrder(new Order("o_9", "delivered", 4999, ""));
  }

  // end::class[]

  // tag::first-case[]
  @Test
  public void looksTheOrderUpBeforeAnswering() {
    var report = new ExperimentRunner(testKit)
      .cases(OrderCases.ORDER_STATUS) // <1>
      .agent(OrderAgent::ask) // <2>
      .run(); // <3>

    assertThat(report.passed()).withFailMessage(report::render).isTrue(); // <4>
  }

  // end::first-case[]

  // tag::batch[]
  @Test
  public void qualityGate() {
    var report = new ExperimentRunner(testKit)
      .cases(OrderCases.CURATED)
      .agent(OrderAgent::ask)
      .gate(
        Gate.passRateShouldBeAtLeast(0.9) // <1>
          .and(Gate.passRateShouldBeAtLeast("order-lookup", 1.0)) // <2>
          .and(Gate.targetShouldNotFail()) // <3>
      )
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::batch[]

  // tag::repeated-runs[]
  @Test
  public void qualityGateOverRepeatedRuns() {
    var report = new ExperimentRunner(testKit)
      .cases(OrderCases.CURATED)
      .agent(OrderAgent::ask)
      .repeat(3) // <1>
      .gate(Gate.passRateShouldBeAtLeast(0.85)) // <2>
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // end::repeated-runs[]

  // tag::judged-batch[]
  @Test
  public void repliesStayHelpful() {
    var report = new ExperimentRunner(testKit)
      .cases(OrderCases.CURATED)
      .agent(OrderAgent::ask)
      .repeat(3)
      .gate(
        Gate.passRateShouldBeAtLeast("judge", 0.8).and(Gate.targetShouldNotFail()) // <1>
      )
      .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }
  // end::judged-batch[]
}
