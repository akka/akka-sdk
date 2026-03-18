package demo.editorial.application;

import akka.javasdk.agent.task.PolicyResult;
import akka.javasdk.agent.task.TaskCompletionContext;
import akka.javasdk.agent.task.TaskPolicy;
import demo.editorial.application.Publication;

/** All publications require editorial approval before finalising. */
public class PublicationApprovalPolicy implements TaskPolicy<Publication> {

  @Override
  public PolicyResult onCompletion(TaskCompletionContext<Publication> context) {
    return PolicyResult.requireApproval("Publication requires editorial approval");
  }
}
