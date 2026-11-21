/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.NotUsed;
import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskArtifactUpdate;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.impl.client.AgentClientImpl;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
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

  // The events a slow client can fall behind the agent before the agent waits for it
  static final int CLIENT_BUFFER = 256;

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
    var historyLength = A2aRequestHandler.historyLength(request);
    return switch (handler.prepare(request)) {
      case A2aRequestHandler.Continuation continuation ->
          followTaskWorkflow(
              handler
                  .taskWorkflows()
                  .continueWithoutWaiting(
                      handler.skillTable(), continuation.task(), continuation.message())
                  .orElseThrow(() -> A2aRequestHandler.notAccepting(continuation.task())),
              historyLength,
              envelope);
      case A2aRequestHandler.NewTask newTask when newTask.skill().taskWorkflow() != null ->
          followTaskWorkflow(
              handler
                  .taskWorkflows()
                  .startWithoutWaiting(
                      newTask.skill(),
                      newTask.userMessage(),
                      handler.owner(),
                      request.configuration()),
              historyLength,
              envelope);
      case A2aRequestHandler.NewTask newTask -> streamShortTask(newTask, historyLength, envelope);
    };
  }

  // The agent's answer runs to its end and is recorded in the task even when the client
  // disconnects, so that the client can resubscribe to the task. The client receives the events
  // through a queue that the answer feeds.
  private HttpResponse streamShortTask(
      A2aRequestHandler.NewTask newTask,
      Integer historyLength,
      Function<A2aStreamResponse, Object> envelope) {
    var working = handler.startShortTask(newTask);
    var outcome = new Outcome(working);
    var contextId = working.contextId();
    var body =
        isStreamingAgent(newTask.skill().agentId())
            ? tokenEvents(outcome, newTask.skill(), contextId, newTask.input())
            : replyEvents(outcome, newTask.skill(), contextId, newTask.input());
    var queue =
        Source.<A2aStreamResponse>queue(CLIENT_BUFFER, OverflowStrategy.backpressure())
            .preMaterialize(handler.materializer);
    var client = queue.first();
    body.mapAsync(
            1,
            event ->
                client
                    .offer(event)
                    // A client that has gone fails the offer, and the answer runs on
                    .handle((result, error) -> Done.getInstance()))
        .runWith(Sink.ignore(), handler.materializer)
        .whenComplete(
            (done, error) -> {
              if (error == null) {
                client.complete();
              } else {
                log.error("The stream of A2A task [{}] failed", working.id(), error);
                client.fail(error);
              }
            });
    var events =
        Source.single(A2aStreamResponse.of(A2aRequestHandler.trimHistory(working, historyLength)))
            .concat(queue.second())
            .map(envelope::apply);
    return HttpResponses.serverSentEvents(events);
  }

  // A task answered by a task workflow: the task, then its changes until it ends or asks for input
  private HttpResponse followTaskWorkflow(
      A2aTaskEntity.State delivered,
      Integer historyLength,
      Function<A2aStreamResponse, Object> envelope) {
    var first =
        A2aStreamResponse.of(A2aRequestHandler.trimHistory(delivered.task(), historyLength));
    var events =
        delivered.task().status().state().terminal()
            ? Source.single(first)
            : Source.single(first).concat(updates(delivered, true));
    return HttpResponses.serverSentEvents(events.map(envelope::apply));
  }

  // The state of one streamed answer: the artifact id that every chunk and the stored artifact use
  private static final class Outcome {
    final A2aTask working;
    final String artifactId = UUID.randomUUID().toString();

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
    return tokens
        // The runtime does not end the token stream of every model that fails, so a stream that
        // stays silent too long counts as a failure
        .idleTimeout(handler.settings.streamIdleTimeout())
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
    var change = handler.outcome(working, finished.result(), finished.error(), outcome.artifactId);
    return handler
        .store
        .changeAsync(working.id(), change)
        .thenCompose(
            changed ->
                changed.isPresent()
                    ? CompletableFuture.completedFuture(changed.get())
                    : handler
                        .componentClient
                        .forKeyValueEntity(working.id())
                        .method(A2aTaskEntity::get)
                        .invokeAsync()
                        .thenApply(A2aTaskEntity.Lookup::state))
        .thenApply(
            state -> {
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
    var events = Source.single(A2aStreamResponse.of(current)).concat(updates(state, false));
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
  // read of the entity, and de-duplicated by version. Ends after the first terminal state, and
  // after the first interrupted state (input-required, auth-required) when endWhenInterrupted.
  private Source<A2aStreamResponse, NotUsed> updates(
      A2aTaskEntity.State start, boolean endWhenInterrupted) {
    var entity = handler.componentClient.forKeyValueEntity(start.task().id());
    var notifications =
        entity
            .notificationStream(A2aTaskEntity::updates)
            .source()
            .map(notification -> new Snapshot(notification.task(), notification.version()));
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
            event -> {
              if (!(event instanceof A2aStreamResponse.StatusUpdate update)) return true;
              var state = update.statusUpdate().status().state();
              boolean interrupted =
                  state == A2aTaskState.INPUT_REQUIRED || state == A2aTaskState.AUTH_REQUIRED;
              return !(state.terminal() || (endWhenInterrupted && interrupted));
            },
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
