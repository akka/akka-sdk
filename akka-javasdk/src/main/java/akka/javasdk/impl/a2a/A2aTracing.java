/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * INTERNAL API
 *
 * <p>The span attributes of A2A calls, in and out. They follow the runtime's naming: {@code
 * akka.<area>.<name>} with dots between the words.
 */
@InternalApi
public final class A2aTracing {

  private A2aTracing() {}

  /** The A2A method, such as {@code SendMessage}. */
  public static final AttributeKey<String> METHOD = AttributeKey.stringKey("akka.a2a.method");

  /** The id of the skill that the message selects. */
  public static final AttributeKey<String> SKILL_ID = AttributeKey.stringKey("akka.a2a.skill.id");

  public static final AttributeKey<String> TASK_ID = AttributeKey.stringKey("akka.a2a.task.id");

  public static final AttributeKey<String> CONTEXT_ID =
      AttributeKey.stringKey("akka.a2a.context.id");

  /** The state of the task when the call ended, as its wire name. */
  public static final AttributeKey<String> TASK_STATE =
      AttributeKey.stringKey("akka.a2a.task.state");

  /** The subject of the caller's JWT, on calls to an A2A endpoint. */
  public static final AttributeKey<String> CALLER_SUBJECT =
      AttributeKey.stringKey("akka.a2a.caller.subject");

  /** The name of the remote agent, on calls to a remote agent. */
  public static final AttributeKey<String> REMOTE_AGENT_NAME =
      AttributeKey.stringKey("akka.a2a.remote.agent.name");

  /** The URL of the remote agent's Agent Card, without user info, query and fragment. */
  public static final AttributeKey<String> REMOTE_CARD_URL =
      AttributeKey.stringKey("akka.a2a.remote.card.url");

  /** The component that called the remote agent. */
  public static final AttributeKey<String> CALLER_COMPONENT_ID =
      AttributeKey.stringKey("akka.a2a.caller.component.id");

  /** The URL without user info, query and fragment, which can hold credentials. */
  public static String withoutCredentials(String url) {
    try {
      var uri = new URI(url);
      if (uri.isOpaque() || uri.getRawAuthority() == null) return "<invalid URI>";
      var authority = uri.getRawAuthority();
      var hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
      var path = uri.getRawPath() == null ? "" : uri.getRawPath();
      return uri.getScheme() + "://" + hostAndPort + path;
    } catch (URISyntaxException e) {
      return "<invalid URI>";
    }
  }

  /** Sets a string attribute when the value is present. */
  public static void set(Optional<Span> span, AttributeKey<String> key, String value) {
    if (value != null && !value.isEmpty()) span.ifPresent(s -> s.setAttribute(key, value));
  }

  /** Sets the task id, context id and state of a task. */
  public static void task(Optional<Span> span, A2aTask task) {
    if (task == null) return;
    set(span, TASK_ID, task.id());
    set(span, CONTEXT_ID, task.contextId());
    if (task.status() != null && task.status().state() != null)
      set(span, TASK_STATE, task.status().state().wireName());
  }

  /** Sets the task attributes of a reply, when the reply is a task. */
  public static void reply(Optional<Span> span, Object reply) {
    switch (reply) {
      case A2aTask task -> task(span, task);
      case A2aSendMessageResponse.Task t -> task(span, t.task());
      case A2aSendMessageResponse.Message m -> set(span, CONTEXT_ID, m.message().contextId());
      default -> {}
    }
  }

  /** Sets the task attributes of a streamed event. */
  public static void event(Optional<Span> span, A2aStreamResponse event) {
    switch (event) {
      case A2aStreamResponse.Task t -> task(span, t.task());
      case A2aStreamResponse.StatusUpdate s -> {
        set(span, TASK_ID, s.statusUpdate().taskId());
        var status = s.statusUpdate().status();
        if (status != null && status.state() != null)
          set(span, TASK_STATE, status.state().wireName());
      }
      case A2aStreamResponse.ArtifactUpdate a -> set(span, TASK_ID, a.artifactUpdate().taskId());
      case A2aStreamResponse.Message m -> set(span, CONTEXT_ID, m.message().contextId());
    }
  }

  public static void state(Optional<Span> span, A2aTaskState state) {
    if (state != null) set(span, TASK_STATE, state.wireName());
  }

  /** Ends the span, with an error status when the call failed. */
  public static void end(Optional<Span> span, Throwable error) {
    span.ifPresent(
        s -> {
          if (error != null) {
            s.recordException(error);
            s.setStatus(StatusCode.ERROR, String.valueOf(error.getMessage()));
          }
          s.end();
        });
  }
}
