package com.example.tracing.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("tracing-action")
public class TracingAction extends TimedAction {

  private static final Logger logger = LoggerFactory.getLogger(TracingAction.class);

  private final Typicode typicode = new Typicode();

  public Effect callAnotherService(String postID) {
    logger.info("Calling to [{}].", Typicode.url + "/" + postID);
    Optional<Span> maybeSpan = commandContext()
      .tracing()
      .startSpan("ad-hoc span calling to: " + Typicode.url);

    maybeSpan.ifPresent(span -> span.setAttribute("post", postID));

    CompletionStage<HttpResponse<Typicode.TypicodePost>> asyncResult =
      typicode.callAsyncService(postID, maybeSpan);

    maybeSpan.ifPresent(span ->
      asyncResult.whenComplete((response, ex) -> {
        if (ex != null) {
          span.setStatus(StatusCode.ERROR, ex.getMessage()).end();
        } else {
          span.setAttribute("response-status", response.statusCode()).end();
        }
      }));

    return effects().asyncEffect(asyncResult.thenApply(__ -> effects().done()));
  }
}
