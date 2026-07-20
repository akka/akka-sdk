package com.example.classifier;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

public class ClassifierTestSample extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      """
      akka.javasdk.agent.classifiers.toxicity {
        class = "com.example.classifier.ToxicityClassifier"
        threshold = 0.5
      }
      """
    );
  }

  @Test
  public void classifiesText() {
    // tag::classify[]
    var classifierClient = getClassifierClient();
    var classification = classifierClient.classify("toxicity", "some input text");
    assertThat(classification.label()).contains("clean");
    // end::classify[]
  }
}
