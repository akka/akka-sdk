package com.example;

import akka.javasdk.agent.evaluator.HallucinationEvaluator;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is no guarantee that these tests will always succeed, because of the non-determinism in AI answers.
 */
public class HallucinationIntegrationTest extends TestKitSupport {

  @Test
  public void testHallucination1() {
    var result =
        componentClient.forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(HallucinationEvaluator::evaluate)
            .invoke(new HallucinationEvaluator.EvaluationRequest(
                """
                What was the primary reason for the delay in the KF-21 prototype's first flight?
                """,
                """
                Project KF-21, a joint initiative between South Korea and Indonesia, aims to develop an advanced multirole fighter jet. The initial timeline scheduled the first flight of the prototype, designated No. 001, for early 2022. However, several factors impacted the schedule. A key supplier for the landing gear avionics, based in Europe, faced significant production disruptions due to supply chain shortages that emerged in late 2021. Separately, the final integration of the active electronically scanned array (AESA) radar system required more extensive software validation than originally anticipated. The prototype eventually achieved its successful maiden flight in July 2022.
                """,
        """
               The primary reason for the delay in the KF-21 prototype's first flight was disruptions at a European supplier responsible for landing gear components, which were caused by supply chain shortages.
               """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isFalse();
  }

  @Test
  public void testFactual1() {
    var result =
        componentClient.forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(HallucinationEvaluator::evaluate)
            .invoke(new HallucinationEvaluator.EvaluationRequest(
                """
                What was the primary reason for the delay in the KF-21 prototype's first flight?
                """,
                """
                Project KF-21, a joint initiative between South Korea and Indonesia, aims to develop an advanced multirole fighter jet. The initial timeline scheduled the first flight of the prototype, designated No. 001, for early 2022. However, several factors impacted the schedule. A key supplier for the landing gear avionics, based in Europe, faced significant production disruptions due to supply chain shortages that emerged in late 2021. Separately, the final integration of the active electronically scanned array (AESA) radar system required more extensive software validation than originally anticipated. The prototype eventually achieved its successful maiden flight in July 2022.
                """,
                """
                       The KF-21 prototype's first flight was delayed by two factors: production disruptions at a key European supplier for landing gear avionics and the need for more extensive software validation for the AESA radar system than originally anticipated.
                       """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isTrue();
  }

  @Test
  public void testHallucination2() {
    var result =
        componentClient.forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(HallucinationEvaluator::evaluate)
            .invoke(new HallucinationEvaluator.EvaluationRequest(
                """
                What are the key features and price of the new Starlight smartwatch?
                """,
                """
                Tech innovator "Starlight" unveiled its much-anticipated wearable device today. The smartwatch, built with a titanium-alloy casing, features a vibrant high-resolution display that remains visible even in direct sunlight. A major focus of the device is its advanced health monitoring capabilities, which include a next-generation optical heart rate sensor and a new biosensor for tracking sleep cycle quality. The watch also promises a multi-day battery life, a significant improvement over previous models, and offers seamless integration with both iOS and Android platforms via a dedicated companion app. The official release is scheduled for next month.
                """,
                """
                       The new Starlight smartwatch features a titanium-alloy casing, a high-resolution display, and advanced health sensors. Its key innovation is the "Chrono-Sync Engine" which improves battery performance. The device is priced at $129 and will be released next month.
                       """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isFalse();
  }

  @Test
  public void testFactual2() {
    var result =
        componentClient.forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(HallucinationEvaluator::evaluate)
            .invoke(new HallucinationEvaluator.EvaluationRequest(
                """
                What are the key features and price of the new Starlight smartwatch?
                """,
                """
                Tech innovator "Starlight" unveiled its much-anticipated wearable device today. The smartwatch, built with a titanium-alloy casing, features a vibrant high-resolution display that remains visible even in direct sunlight. A major focus of the device is its advanced health monitoring capabilities, which include a next-generation optical heart rate sensor and a new biosensor for tracking sleep cycle quality. The watch also promises a multi-day battery life, a significant improvement over previous models, and offers seamless integration with both iOS and Android platforms via a dedicated companion app. The official release is scheduled for next month.
                """,
                """
                       The new Starlight smartwatch has a titanium-alloy casing and a high-resolution display visible in sunlight. Its main features are advanced health monitoring, including a heart rate sensor and sleep tracker, a multi-day battery life, and compatibility with both iOS and Android devices through a companion app.
                       """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isTrue();
  }

}
