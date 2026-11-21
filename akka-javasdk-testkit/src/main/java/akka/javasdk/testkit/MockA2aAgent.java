/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aAgentCard;
import akka.javasdk.a2a.A2aAgentSkill;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.impl.a2a.A2aErrorCode;
import akka.javasdk.impl.a2a.JsonRpcError;
import akka.javasdk.impl.a2a.JsonRpcResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A remote A2A agent for tests. It runs a local server that serves an Agent Card and answers the
 * A2A methods {@code SendMessage}, {@code GetTask} and {@code CancelTask} on the JSON-RPC binding.
 * Each skill has a {@link Reply} that the test chooses.
 *
 * <p>Start the mock before the TestKit and give it to the settings with {@link
 * TestKit.Settings#withMockedA2aAgent(String, MockA2aAgent)}, which names it in the configuration
 * as a remote A2A agent:
 *
 * <pre>{@code
 * static final MockA2aAgent claims =
 *     MockA2aAgent.create("claims")
 *         .withSkill("claim-status", "Looks up a claim", MockA2aAgent.Reply.text("Claim 42 is paid"))
 *         .start();
 *
 * protected TestKit.Settings testKitSettings() {
 *   return TestKit.Settings.DEFAULT.withMockedA2aAgent("claims", claims);
 * }
 * }</pre>
 *
 * <p>The mock does not send push notifications, so a workflow that follows a task of the mock polls
 * for it. A message that names no skill goes to the first skill. A mock that is not closed keeps
 * its port until the JVM ends.
 */
public final class MockA2aAgent implements AutoCloseable {

  /** What the mock answers to a {@code SendMessage} for one skill. */
  public static final class Reply {

    private enum Kind {
      TEXT,
      DATA,
      WORKING_THEN_COMPLETED,
      FAILED_TASK,
      ERROR
    }

    private final Kind kind;
    private final String text;
    private final Map<String, Object> data;
    private final Duration workingFor;

    private Reply(Kind kind, String text, Map<String, Object> data, Duration workingFor) {
      this.kind = kind;
      this.text = text;
      this.data = data;
      this.workingFor = workingFor;
    }

    /** A message with one text part. */
    public static Reply text(String text) {
      return new Reply(Kind.TEXT, text, null, null);
    }

    /** A message with one data part. */
    public static Reply data(Map<String, Object> data) {
      return new Reply(Kind.DATA, null, data, null);
    }

    /**
     * A task that is working when the mock accepts it. A {@code GetTask} that arrives after {@code
     * workingFor} has passed since then finds the task completed, with an artifact that has one
     * text part. Until then, {@code GetTask} finds it working. A zero duration completes the task
     * at the first {@code GetTask}.
     */
    public static Reply workingThenCompleted(Duration workingFor, String resultText) {
      return new Reply(Kind.WORKING_THEN_COMPLETED, resultText, null, workingFor);
    }

    /** A task that has failed, with the given text as the message of its status. */
    public static Reply failed(String message) {
      return new Reply(Kind.FAILED_TASK, message, null, null);
    }

    /** A JSON-RPC internal error with the given message. */
    public static Reply error(String message) {
      return new Reply(Kind.ERROR, message, null, null);
    }
  }

  /**
   * A {@code SendMessage} that the mock received.
   *
   * @param skillId the skill id in the metadata of the request, or the first skill when the request
   *     names none
   * @param text the text parts of the message, joined with a new line
   * @param data the first data part of the message, or an empty map
   * @param contextId the context id of the message, or null
   * @param taskId the task id of the message, or null
   */
  public record Received(
      String skillId, String text, Map<String, Object> data, String contextId, String taskId) {}

  private record Skill(String id, String description, Reply reply) {}

  private record TaskRecord(String contextId, Reply reply, Instant created) {}

  private static final ObjectMapper mapper = JsonSupport.getObjectMapper();

  private final String name;
  private final List<Skill> skills = new ArrayList<>();
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
  private final Map<String, Boolean> canceled = new ConcurrentHashMap<>();
  private volatile HttpServer server;

  private MockA2aAgent(String name) {
    this.name = name;
  }

  /**
   * Creates a mock whose Agent Card carries the given name. Add skills with {@link #withSkill},
   * then call {@link #start()}.
   */
  public static MockA2aAgent create(String name) {
    return new MockA2aAgent(name);
  }

  /** Adds a skill to the Agent Card, and the reply that the mock gives to messages for it. */
  public MockA2aAgent withSkill(String skillId, String description, Reply reply) {
    if (server != null) throw new IllegalStateException("Add skills before the mock starts");
    skills.add(new Skill(skillId, description, reply));
    return this;
  }

  /** Starts the server on a free local port. */
  public MockA2aAgent start() {
    if (server != null) throw new IllegalStateException("The mock has started already");
    if (skills.isEmpty()) throw new IllegalStateException("Add at least one skill before start()");
    try {
      var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      httpServer.createContext("/.well-known/agent-card.json", this::card);
      httpServer.createContext("/a2a", this::jsonRpc);
      httpServer.start();
      server = httpServer;
      return this;
    } catch (IOException e) {
      throw new IllegalStateException("The mock A2A agent could not start", e);
    }
  }

  /** The URL of the Agent Card, for {@code card-url} in the configuration of a remote agent. */
  public String cardUrl() {
    return baseUrl() + "/.well-known/agent-card.json";
  }

  /** The messages that the mock has received, oldest first. */
  public List<Received> received() {
    return Collections.unmodifiableList(received);
  }

  /** Forgets the messages that the mock has received and the tasks that it has accepted. */
  public void reset() {
    received.clear();
    tasks.clear();
    canceled.clear();
  }

  /** Stops the server. */
  @Override
  public void close() {
    var running = server;
    if (running != null) running.stop(0);
  }

  private String baseUrl() {
    var running = server;
    if (running == null) throw new IllegalStateException("The mock has not started");
    return "http://localhost:" + running.getAddress().getPort();
  }

  private void card(HttpExchange exchange) throws IOException {
    var card =
        new A2aAgentCard(
            name,
            "A mock A2A agent for tests",
            List.of(new A2aAgentCard.Interface(baseUrl() + "/a2a", "JSONRPC", null, "1.0")),
            null,
            "1.0",
            null,
            new A2aAgentCard.Capabilities(false, false, null, false),
            null,
            null,
            List.of("text/plain", "application/json"),
            List.of("text/plain", "application/json"),
            skills.stream()
                .map(
                    s ->
                        new A2aAgentSkill(
                            s.id(), s.id(), s.description(), null, null, null, null, null))
                .toList(),
            null,
            null);
    respond(exchange, mapper.writeValueAsBytes(card));
  }

  private void jsonRpc(HttpExchange exchange) throws IOException {
    var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
    var id = request.get("id");
    var params = request.path("params");
    JsonRpcResponse response;
    switch (request.path("method").asText()) {
      case "SendMessage" -> response = sendMessage(id, params);
      case "GetTask" -> response = getTask(id, params.path("id").asText());
      case "CancelTask" -> response = cancelTask(id, params.path("id").asText());
      default ->
          response = JsonRpcResponse.failure(id, JsonRpcError.of(A2aErrorCode.METHOD_NOT_FOUND));
    }
    respond(exchange, mapper.writeValueAsBytes(response));
  }

  private JsonRpcResponse sendMessage(JsonNode id, JsonNode params) {
    var message = params.path("message");
    var requested =
        params
            .path("metadata")
            .path("skillId")
            .asText(message.path("metadata").path("skillId").asText(null));
    var skill =
        skills.stream()
            .filter(s -> requested == null || s.id().equals(requested))
            .findFirst()
            .orElse(null);
    if (skill == null) {
      return JsonRpcResponse.failure(
          id, JsonRpcError.of(A2aErrorCode.INVALID_PARAMS, "No skill " + requested, null));
    }
    var contextId = textOrNull(message.get("contextId"));
    received.add(
        new Received(
            skill.id(),
            texts(message),
            firstData(message),
            contextId,
            textOrNull(message.get("taskId"))));
    var context = contextId != null ? contextId : UUID.randomUUID().toString();
    var reply = skill.reply();
    return switch (reply.kind) {
      case TEXT ->
          success(id, A2aSendMessageResponse.of(agentMessage(context, A2aPart.text(reply.text))));
      case DATA ->
          success(id, A2aSendMessageResponse.of(agentMessage(context, A2aPart.data(reply.data))));
      case ERROR ->
          JsonRpcResponse.failure(
              id, JsonRpcError.of(A2aErrorCode.INTERNAL_ERROR, reply.text, null));
      case WORKING_THEN_COMPLETED, FAILED_TASK -> {
        var taskId = UUID.randomUUID().toString();
        tasks.put(taskId, new TaskRecord(context, reply, Instant.now()));
        yield success(id, A2aSendMessageResponse.of(task(taskId, true)));
      }
    };
  }

  private JsonRpcResponse getTask(JsonNode id, String taskId) {
    if (!tasks.containsKey(taskId)) {
      return JsonRpcResponse.failure(id, JsonRpcError.of(A2aErrorCode.TASK_NOT_FOUND));
    }
    return success(id, task(taskId, false));
  }

  private JsonRpcResponse cancelTask(JsonNode id, String taskId) {
    if (!tasks.containsKey(taskId)) {
      return JsonRpcResponse.failure(id, JsonRpcError.of(A2aErrorCode.TASK_NOT_FOUND));
    }
    canceled.put(taskId, true);
    return success(id, task(taskId, false));
  }

  private A2aTask task(String taskId, boolean justAccepted) {
    var record = tasks.get(taskId);
    var reply = record.reply();
    if (canceled.containsKey(taskId)) {
      return new A2aTask(
          taskId,
          record.contextId(),
          new A2aTaskStatus(A2aTaskState.CANCELED, null, null),
          null,
          null,
          null);
    }
    if (reply.kind == Reply.Kind.FAILED_TASK) {
      var status =
          new A2aTaskStatus(
              A2aTaskState.FAILED,
              A2aMessage.agent(reply.text).withContextId(record.contextId()),
              null);
      return new A2aTask(taskId, record.contextId(), status, null, null, null);
    }
    var done = !justAccepted && !Instant.now().isBefore(record.created().plus(reply.workingFor));
    if (!done) {
      return new A2aTask(
          taskId,
          record.contextId(),
          new A2aTaskStatus(A2aTaskState.WORKING, null, null),
          null,
          null,
          null);
    }
    var artifact =
        new A2aArtifact("result", "result", null, List.of(A2aPart.text(reply.text)), null, null);
    return new A2aTask(
        taskId,
        record.contextId(),
        new A2aTaskStatus(A2aTaskState.COMPLETED, null, null),
        List.of(artifact),
        null,
        null);
  }

  private static A2aMessage agentMessage(String contextId, A2aPart part) {
    return A2aMessage.agent(part).withContextId(contextId);
  }

  private static JsonRpcResponse success(JsonNode id, Object result) {
    return JsonRpcResponse.success(id, mapper.valueToTree(result));
  }

  private static String texts(JsonNode message) {
    var parts = new ArrayList<String>();
    for (var part : message.path("parts")) {
      if (part.hasNonNull("text")) parts.add(part.get("text").asText());
    }
    return String.join("\n", parts);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstData(JsonNode message) {
    for (var part : message.path("parts")) {
      if (part.hasNonNull("data")) {
        return mapper.convertValue(part.get("data"), LinkedHashMap.class);
      }
    }
    return Map.of();
  }

  private static String textOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }

  private static void respond(HttpExchange exchange, byte[] body) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    try (var out = exchange.getResponseBody()) {
      out.write(body);
    }
    exchange.close();
  }
}
