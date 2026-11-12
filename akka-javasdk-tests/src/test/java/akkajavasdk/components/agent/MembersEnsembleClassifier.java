/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Classification;
import akka.javasdk.agent.Classifier;
import akka.javasdk.agent.ClassifierClient;
import akka.javasdk.agent.ClassifierContext;
import java.util.List;

/** Test classifier: calls each classifier named in its {@code members} config. */
public class MembersEnsembleClassifier implements Classifier {
  private final String name;
  private final List<String> members;
  private final ClassifierClient classifierClient;

  public MembersEnsembleClassifier(ClassifierContext context, ClassifierClient classifierClient) {
    this.name = context.name();
    this.members = context.config().getStringList("members");
    this.classifierClient = classifierClient;
  }

  @Override
  public Classification classify(String input) {
    members.forEach(member -> classifierClient.classify(member, input));
    return Classification.label(name);
  }
}
