package com.example.classifier;

import akka.javasdk.agent.Classification;
import akka.javasdk.agent.ClassifierClient;

public class ClassifierUsage {

  private final ClassifierClient classifierClient;

  public ClassifierUsage(ClassifierClient classifierClient) {
    this.classifierClient = classifierClient;
  }

  void inspect(String text) {
    // tag::invoke[]
    Classification result = classifierClient.classify("toxicity", text); // <1>
    result.label().ifPresent(label -> { /* ... */ });
    result.score().ifPresent(score -> { /* ... */ });
    // end::invoke[]
  }
}
