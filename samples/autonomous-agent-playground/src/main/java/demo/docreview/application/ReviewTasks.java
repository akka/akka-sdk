/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docreview.application;

import akka.javasdk.agent.task.Task;

/** Task definitions for document review. */
public class ReviewTasks {

  // prettier-ignore
  public static final Task<ReviewResult> REVIEW = Task
    .define("Review")
    .description("Review a document for compliance")
    .resultConformsTo(ReviewResult.class);
}
