package com.example.classifier;

// tag::all[]
import akka.javasdk.agent.Classification;
import akka.javasdk.agent.Classifier;
import akka.javasdk.agent.ClassifierContext;

public class ToxicityClassifier implements Classifier { // <1>

  private final double threshold;

  public ToxicityClassifier(ClassifierContext context) { // <2>
    this.threshold = context.config().getDouble("threshold");
  }

  @Override
  public Classification classify(String input) { // <3>
    double score = score(input); // a real implementation might call a model or an external API
    String label = score >= threshold ? "toxic" : "clean";
    return Classification.of(score, label); // <4>
  }

  private double score(String input) {
    return input.toLowerCase().contains("toxic") ? 1.0 : 0.0;
  }
}
// end::all[]
