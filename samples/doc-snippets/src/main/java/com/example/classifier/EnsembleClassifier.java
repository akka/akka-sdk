package com.example.classifier;

// tag::all[]
import akka.javasdk.agent.Classification;
import akka.javasdk.agent.Classifier;
import akka.javasdk.agent.ClassifierClient;
import akka.javasdk.agent.ClassifierContext;
import java.util.List;

public class EnsembleClassifier implements Classifier {

  private final ClassifierClient classifierClient; // <1>
  private final List<String> members;

  public EnsembleClassifier(ClassifierContext context, ClassifierClient classifierClient) { // <2>
    this.classifierClient = classifierClient;
    this.members = context.config().getStringList("members");
  }

  @Override
  public Classification classify(String input) {
    double worst = members
      .stream()
      .map(name -> classifierClient.classify(name, input)) // <3>
      .flatMap(c -> c.score().stream())
      .max(Double::compare)
      .orElse(0.0);
    return Classification.score(worst); // <4>
  }
}
// end::all[]
