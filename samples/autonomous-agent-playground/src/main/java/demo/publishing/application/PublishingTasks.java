package demo.publishing.application;

import akka.javasdk.agent.task.Task;

/** Task definitions for the publishing approval pipeline: draft → approval → publish. */
public class PublishingTasks {

  // prettier-ignore
  public static final Task<DraftPost> DRAFT = Task
    .define("Draft post")
    .description("Draft a blog post on a given topic")
    .resultConformsTo(DraftPost.class);

  // prettier-ignore
  public static final Task<ApprovalDecision> APPROVAL = Task
    .define("Approval")
    .description("Human approval gate for publishing")
    .resultConformsTo(ApprovalDecision.class);

  // prettier-ignore
  public static final Task<PublishedPost> PUBLISH = Task
    .define("Publish post")
    .description("Publish an approved post")
    .resultConformsTo(PublishedPost.class);
}
