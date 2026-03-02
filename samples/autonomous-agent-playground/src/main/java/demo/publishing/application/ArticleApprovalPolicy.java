package demo.publishing.application;

import akka.javasdk.agent.task.PolicyResult;
import akka.javasdk.agent.task.TaskCompletionContext;
import akka.javasdk.agent.task.TaskPolicy;
import demo.publishing.application.Article;

/**
 * All articles require editorial approval before publication. This policy intercepts task
 * completion and pauses the task in AWAITING_APPROVAL status so a human reviewer can approve or
 * reject.
 */
public class ArticleApprovalPolicy implements TaskPolicy<Article> {

  @Override
  public PolicyResult onCompletion(TaskCompletionContext<Article> context) {
    return PolicyResult.requireApproval(
      "Article requires editorial review before publication"
    );
  }
}
