/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Classification;
import akka.javasdk.agent.Classifier;
import akka.javasdk.agent.ClassifierContext;
import akkajavasdk.components.OverridableDependency;

/** Test classifier that labels every input with the name of the injected dependency. */
public class DependencyOverrideClassifier implements Classifier {
  private final OverridableDependency dependency;

  public DependencyOverrideClassifier(ClassifierContext context, OverridableDependency dependency) {
    this.dependency = dependency;
  }

  @Override
  public Classification classify(String input) {
    return Classification.label(dependency.name());
  }
}
