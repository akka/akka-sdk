/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.NotUsed;
import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aSendMessageRequest;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aSubscribeToTaskRequest;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskArtifactUpdate;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.impl.client.AgentClientImpl;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>The streaming methods of A2A as server-sent events: {@code SendStreamingMessage}, which
 * streams the answer of an agent, and {@code SubscribeToTask}, which follows the changes of a task
 * in {@link A2aTaskEntity}.
 *
 * <p>A stream of a task starts with the task, and continues with status updates and artifact
 * updates. It ends when the task reaches a terminal state.
 */
@InternalApi
final class A2aStreaming {

  private static final Logger log = LoggerFactory.getLogger(A2aStreaming.class);

  // Entity notifications are live only. A change between reading the task and the notification
  // stream connecting is caught by reading the task again.
  static final Duration FIRST_RE_READ = Duration.ofMillis(500);
  static final Duration RE_READ_INTERVAL = Duration.ofSeconds(2);

  private final A2aRequestHandler handler;

  A2aStreaming(A2aRequestHandler handler) {
    this.handler = handler;
  }

  /**
   * The JSON-RPC streaming methods.
   *
   * @return the streaming response, or empty when the method is not a streaming method
   */
  Optional<HttpResponse> jsonRpc(String method, JsonNode id, JsonNode params) {
    Function<A2aStreamResponse, Object> envelope =
        response -> JsonRpcResponse.success(id, handler.mapper.valueToTree(response));
    return switch (method) {
      case A2aMethods.SEND_STREAMING_MESSAGE ->
          Optional.of(
              sendStreamingMessage(
                  handler.read(params, A2aSendMessageRequest.class, A2aErrorCode.INVALID_PARAMS),
                  envelope));
      case A2aMethods.SUBSCRIBE_TO_TASK -> {
        var request =
            handler.read(params, A2aSubscribeToTaskRequest.class, A2aErrorCode.INVALID_PARAMS);
        yield Optional.of(subscribeToTask(request.id(), envelope));
      }
      default -> Optional.empty();
    };
  }

  // ---------------------------------------------------------------------------------------------
  // SendStreamingMessage

  HttpResponse sendStreamingMessage(
      A2aSendMessageRequest request, Function<A2aStreamResponse, Object> envelope) {
    var message = request.message();
    if (message == null) throw A2aRequestHandler.invalidParams("The request has no message");
    if (A2aRequestHandler.isBlank(message.messageId())) {
      throw A2aRequestHandler.invalidParams("The message has no messageId");
    }
    if (message.parts().isEmpty())
      throw A2aRequestHandler.invalidParams("The message has no parts");
    if (message.role() == A2aRole.AGENT) {
      throw A2aRequestHandler.invalidParams("The message has the role ROLE_AGENT");
    }
    var configuration = request.configuration();
    Integer historyLength = configuration == null ? null : configuration.historyLength();
    A2aRequestHandler.checkHistoryLength(historyLength);

    if (!A2aRequestHandler.isBlank(message.taskId())) {
      var existing = handler.lookup(message.taskId());
      if (!A2aRequestHandler.isBlank(message.contextId())
          && !message.contextId().equals(existing.contextId())) {
        throw A2aRequestHandler.invalidParams(
            "The contextId of the message does not match the context of task ["
                + message.taskId()
                + "]");
      }
      throw new A2aException(
          A2aErrorCode.UNSUPPORTED_OPERATION,
          existing.status().state().terminal()
              ? "Task [" + message.taskId() + "] has ended and accepts no more messages"
              : "Task [" + message.taskId() + "] is not waiting for input");
    }

    var contextId =
        A2aRequestHandler.isBlank(message.contextId())
            ? UUID.randomUUID().toString()
            : message.contextId();
    var taskId = UUID.randomUUID().toString();
    handler.bind(contextId, taskId);

    var skill = handler.selectSkill(message, request.metadata());
    var input = handler.agentInput(skill, message);

    var userMessage =
        new A2aMessage(
            message.messageId(),
            contextId,
            taskId,
            A2aRole.USER,
            message.parts(),
            message.metadata(),
            message.extensions(),
            message.referenceTaskIds());
    var submitted =
        new A2aTask(
            taskId,
            contextId,
            A2aRequestHandler.status(A2aTaskState.SUBMITTED),
            null,
            List.of(userMessage),
            null);
    handler
        .componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskEntity::create)
        .invoke(new A2aTaskEntity.Create(submitted, skill.skill().id(), handler.owner()));
    var working =
        A2aRequestHandler.withStatus(submitted, A2aRequestHandler.status(A2aTaskState.WORKING));
    handler.store(working);

    var outcome = new Outcome(working);
    var body =
        isStreamingAgent(skill.agentId())
            ? tokenEvents(outcome, skill, contextId, input)
            : replyEvents(outcome, skill, contextId, input);
    var events =
        Source.single(A2aStreamResponse.of(A2aRequestHandler.trimHistory(working, historyLength)))
            .concat(body)
            // A client that disconnects before the answer is complete cancels the task.
            .watchTermination(
                (notUsed, done) -> {
                  done.whenComplete(
                      (ok, error) -> {
                        if (!outcome.recorded.get()) cancelAfterDisconnect(taskId);
                      });
                  return notUsed;
                })
            .map(envelope::apply);
    return HttpResponses.serverSentEvents(events);
  }

  // The state of one streamed answer: the artifact id that every chunk uses, and whether the
  // outcome has been written to the task.
  private static final class Outcome {
    final A2aTask working;
    final String artifactId = UUID.randomUUID().toString();
    final AtomicBoolean recorded = new AtomicBoolean(false);

    Outcome(A2aTask working) {
      this.working = working;
    }
  }

  private record Finished(Object result, Throwable error) {}

  // An agent that replies in one piece: one artifact update, then the final status.
  private Source<A2aStreamResponse, NotUsed> replyEvents(
      Outcome outcome, A2aSkillTable.Entry skill, String contextId, Object input) {
    return Source.completionStage(
            handler
                .callAgent(skill, contextId, input)
                .handle((result, error) -> new Finished(result, error)))
        .mapAsync(1, finished -> record(outcome, finished, false))
        .mapConcat(events -> events);
  }

  // An agent that streams tokens: each token is a chunk of one artifact. The last chunk is held
  // back by one element so that it can carry lastChunk.
  private Source<A2aStreamResponse, NotUsed> tokenEvents(
      Outcome outcome, A2aSkillTable.Entry skill, String contextId, Object input) {
    Source<String, NotUsed> tokens;
    try {
      tokens = agentClient(contextId).dynamicTokenStream(skill.agentId(), input);
    } catch (RuntimeException e) {
      tokens = Source.failed(e);
    }
    var taskId = outcome.working.id();
    return tokens
        .<Object>map(token -> token)
        .recover(new PFBuilder<Throwable, Object>().match(Throwable.class, error -> error).build())
        .statefulMap(
            TokenFold::new,
            (fold, piece) -> {
              var events = new ArrayList<Object>();
              if (piece instanceof Throwable error) {
                fold.failure = error;
              } else {
                if (fold.pending != null) {
                  events.add(chunk(outcome, fold.pending, fold.sent, false));
                  fold.sent = true;
                }
                fold.pending = (String) piece;
                fold.text.append(fold.pending);
              }
              return Pair.create(fold, events);
            },
            fold -> {
              var events = new ArrayList<Object>();
              if (fold.pending != null) {
                events.add(chunk(outcome, fold.pending, fold.sent, fold.failure == null));
              }
              events.add(new Finished(fold.text.toString(), fold.failure));
              return Optional.of(events);
            })
        .mapConcat(events -> events)
        .mapAsync(
            1,
            event ->
                event instanceof Finished finished
                    ? record(outcome, finished, true)
                    : CompletableFuture.completedFuture(List.of((A2aStreamResponse) event)))
        .mapConcat(events -> events);
  }

  private static final class TokenFold {
    String pending;
    boolean sent = false;
    final StringBuilder text = new StringBuilder();
    Throwable failure;
  }

  private A2aStreamResponse chunk(Outcome outcome, String text, boolean append, boolean last) {
    var artifact =
        new A2aArtifact(
            outcome.artifactId, "response", null, List.of(A2aPart.text(text)), null, null);
    return A2aStreamResponse.of(
        new A2aTaskArtifactUpdate(
            outcome.working.id(), outcome.working.contextId(), artifact, append, last, null));
  }

  // Writes the outcome to the task, and returns the events that complete the stream. A task that
  // was canceled meanwhile stays canceled.
  private CompletionStage<List<A2aStreamResponse>> record(
      Outcome outcome, Finished finished, boolean chunksSent) {
    var working = outcome.working;
    var done = handler.finish(working, finished.result(), finished.error());
    var withArtifactId =
        done.artifacts().isEmpty()
            ? done
            : new A2aTask(
                done.id(),
                done.contextId(),
                done.status(),
                done.artifacts().stream()
                    .map(
                        a ->
                            new A2aArtifact(
                                outcome.artifactId,
                                a.name(),
                                a.description(),
                                a.parts(),
                                a.metadata(),
                                a.extensions()))
                    .toList(),
                done.history(),
                done.metadata());
    return handler
        .componentClient
        .forKeyValueEntity(working.id())
        .method(A2aTaskEntity::update)
        .invokeAsync(withArtifactId)
        .thenApply(
            state -> {
              outcome.recorded.set(true);
              var events = new ArrayList<A2aStreamResponse>();
              var task = state.task();
              // Chunks of a streamed answer were sent already. A reply in one piece is sent now.
              if (task.status().state() == A2aTaskState.COMPLETED && !chunksSent) {
                task.artifacts()
                    .forEach(
                        artifact ->
                            events.add(
                                A2aStreamResponse.of(
                                    new A2aTaskArtifactUpdate(
                                        task.id(),
                                        task.contextId(),
                                        artifact,
                                        false,
                                        true,
                                        null))));
              }
              events.add(
                  A2aStreamResponse.of(
                      new A2aTaskStatusUpdate(task.id(), task.contextId(), task.status(), null)));
              return List.copyOf(events);
            });
  }

  private void cancelAfterDisconnect(String taskId) {
    handler
        .componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskEntity::cancel)
        .invokeAsync()
        .whenComplete(
            (result, error) -> {
              if (error != null) log.warn("Failed to cancel A2A task [{}]", taskId, error);
            });
  }

  private AgentClientImpl agentClient(String contextId) {
    return (AgentClientImpl) handler.componentClient.forAgent().inSession(contextId);
  }

  // Whether the agent of the skill answers with a token stream.
  private boolean isStreamingAgent(String agentId) {
    for (Class<? extends Agent> agentClass : handler.endpoint()._internalAgents().agents()) {
      var component = agentClass.getAnnotation(Component.class);
      if (component != null && component.id().equals(agentId)) {
        for (Method method : agentClass.getMethods()) {
          if (Agent.StreamEffect.class.isAssignableFrom(method.getReturnType())) return true;
        }
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------
  // SubscribeToTask

  HttpResponse subscribeToTask(String taskId, Function<A2aStreamResponse, Object> envelope) {
    if (A2aRequestHandler.isBlank(taskId)) {
      throw A2aRequestHandler.invalidParams("The request has no task id");
    }
    var state = lookupState(taskId);
    var current = state.task();
    handler.bind(current.contextId(), current.id());
    if (current.status().state().terminal()) {
      throw new A2aException(
          A2aErrorCode.UNSUPPORTED_OPERATION,
          "Task [" + taskId + "] has ended and cannot be subscribed to");
    }
    var events = Source.single(A2aStreamResponse.of(current)).concat(updates(state));
    return HttpResponses.serverSentEvents(events.map(envelope::apply));
  }

  private record Snapshot(A2aTask task, long version) {}

  private static final class Last {
    A2aTask task;
    long version;

    Last(A2aTask task, long version) {
      this.task = task;
      this.version = version;
    }
  }

  // The changes of a task after the given version: entity notifications, merged with a periodic
  // read of the entity, and de-duplicated by version. Ends after the first terminal state.
  private Source<A2aStreamResponse, NotUsed> updates(A2aTaskEntity.State start) {
    var entity = handler.componentClient.forKeyValueEntity(start.task().id());
    var notifications =
        entity
            .notificationStream(A2aTaskEntity::updates)
            .source()
            .map(changed -> new Snapshot(changed.task(), changed.version()));
    var reads =
        Source.tick(FIRST_RE_READ, RE_READ_INTERVAL, NotUsed.getInstance())
            .mapAsync(1, tick -> entity.method(A2aTaskEntity::get).invokeAsync())
            .filter(lookup -> lookup.state() != null)
            .map(lookup -> new Snapshot(lookup.state().task(), lookup.state().version()));
    return notifications
        .merge(reads)
        .statefulMap(
            () -> new Last(start.task(), start.version()),
            (last, snapshot) -> {
              if (snapshot.version() <= last.version) {
                return Pair.create(last, List.<A2aStreamResponse>of());
              }
              var changes = diff(last.task, snapshot.task());
              last.task = snapshot.task();
              last.version = snapshot.version();
              return Pair.create(last, changes);
            },
            last -> Optional.empty())
        .mapConcat(events -> events)
        .takeWhile(
            event ->
                !(event.statusUpdate() != null && event.statusUpdate().status().state().terminal()),
            true);
  }

  // The task with its version, for a caller that may see it. A task of another subject is not
  // revealed.
  private A2aTaskEntity.State lookupState(String taskId) {
    var state =
        handler
            .componentClient
            .forKeyValueEntity(taskId)
            .method(A2aTaskEntity::get)
            .invoke()
            .state();
    if (state == null || (state.owner() != null && !state.owner().equals(handler.owner()))) {
      throw new A2aException(A2aErrorCode.TASK_NOT_FOUND, "Task [" + taskId + "] not found");
    }
    return state;
  }

  // Artifacts that are new or different, then the status when it changed.
  private static List<A2aStreamResponse> diff(A2aTask before, A2aTask after) {
    var events = new ArrayList<A2aStreamResponse>();
    for (A2aArtifact artifact : after.artifacts()) {
      boolean unchanged = before.artifacts().stream().anyMatch(artifact::equals);
      if (!unchanged) {
        events.add(
            A2aStreamResponse.of(
                new A2aTaskArtifactUpdate(
                    after.id(), after.contextId(), artifact, false, true, null)));
      }
    }
    if (!after.status().equals(before.status())) {
      events.add(
          A2aStreamResponse.of(
              new A2aTaskStatusUpdate(after.id(), after.contextId(), after.status(), null)));
    }
    return events;
  }
}
