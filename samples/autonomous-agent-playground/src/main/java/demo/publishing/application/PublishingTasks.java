package demo.publishing.application;

import akka.javasdk.agent.task.Task;

public class PublishingTasks {

  public static final Task<Article> ARTICLE = Task.of(
    "Write and publish an article",
    Article.class
  ).policy(ArticleApprovalPolicy.class);
}
