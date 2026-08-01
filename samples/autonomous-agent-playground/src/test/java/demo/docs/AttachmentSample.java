/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docs;

import akka.http.javadsl.model.HttpEntity;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.objectstorage.ObjectStorageProvider;
import demo.docreview.application.ReviewTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Code snippets for the task-attachment sections of the autonomous-agent documentation. Never
 * executed; compiled so the examples stay in step with the SDK.
 */
class AttachmentSample {

  private static final Logger log = LoggerFactory.getLogger(AttachmentSample.class);

  void attachByUri() {
    // tag::attach-uri[]
    var task = ReviewTasks.REVIEW.instructions("Describe this diagram").attach(
      MessageContent.ImageMessageContent.fromUri("https://example.com/diagram.png")
    );
    // end::attach-uri[]
    log.info("Task: {}", task);
  }

  void attachFromObjectStorage(
    ObjectStorageProvider objectStorageProvider,
    String key,
    HttpEntity.Strict body
  ) {
    // tag::attach-object-storage[]
    var imageBucket = objectStorageProvider.forBucket("images");
    imageBucket.put(key, body.getData(), body.getContentType());

    var task = ReviewTasks.REVIEW.instructions("Describe this image").attach(
      MessageContent.ImageUrlMessageContent.create(imageBucket, key)
    );
    // end::attach-object-storage[]
    log.info("Task: {}", task);
  }
}
