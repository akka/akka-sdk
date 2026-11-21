/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.RawHeader;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aAgentCard;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aSecurityRequirement;
import akka.javasdk.a2a.A2aSecurityScheme;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.a2a.A2aEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.http.RequestContext;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentelemetry.api.trace.Span;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>Serves the A2A routes of one request to an {@link AbstractA2aEndpoint}: the JSON-RPC
 * dispatcher, the HTTP+JSON routes and the Agent Card. A short skill is answered by one call to its
 * agent, with the A2A context id as the session id, and its task is recorded in {@link
 * A2aTaskEntity}.
 */
@InternalApi
public final class A2aRequestHandler {

  private static final Logger log = LoggerFactory.getLogger(A2aRequestHandler.class);

  public static final String PROTOCOL_VERSION = "1.0";
  static final String VERSION_HEADER = "A2A-Version";
  static final String SKILL_ID = "skillId";
  private static final String CANCEL_SUFFIX = ":cancel";
  private static final String SUBSCRIBE_SUFFIX = ":subscribe";
  static final String CARD_CACHE_CONTROL = "max-age=300";
  static final String ERROR_INFO_TYPE = "type.googleapis.com/google.rpc.ErrorInfo";
  static final List<String> DEFAULT_INPUT_MODES = List.of("text/plain", "application/json", "*/*");
  static final List<String> DEFAULT_OUTPUT_MODES = List.of("text/plain", "application/json");

  final ObjectMapper mapper = JsonSupport.getObjectMapper();

  private final AbstractA2aEndpoint endpoint;
  private final A2aEndpoint annotation;
  private final JWT jwt;
  final ComponentClient componentClient;
  final A2aEndpointSettings settings;
  final Materializer materializer;
  final A2aTaskStore store;
  private final RequestContext httpContext;
  private final AgentRegistry agentRegistry;
  private final Optional<String> requestHost;
  private final A2aRequestContextImpl requestContext;
  private final A2aStreaming streaming = new A2aStreaming(this);
  private final A2aTaskOperations operations = new A2aTaskOperations(this);

  // The span of the A2A method of this request, which is handled by this instance alone
  private Optional<Span> span = Optional.empty();
  // True once a stream answers the request: the span then ends with the stream
  private boolean streamed = false;
  private Throwable failure = null;

  /**
   * @param requestContext the context that the endpoint was created with
   * @param requestHost the {@code Host} header of the request
   */
  public A2aRequestHandler(
      AbstractA2aEndpoint endpoint,
      A2aRequestContextImpl requestContext,
      ComponentClient componentClient,
      RequestContext httpContext,
      AgentRegistry agentRegistry,
      A2aEndpointSettings settings,
      Optional<String> requestHost,
      Materializer materializer) {
    this.endpoint = endpoint;
    this.annotation = endpoint.getClass().getAnnotation(A2aEndpoint.class);
    this.jwt = endpoint.getClass().getAnnotation(JWT.class);
    this.requestContext = requestContext;
    this.componentClient = componentClient;
    this.store = new A2aTaskStore(componentClient);
    this.httpContext = httpContext;
    this.agentRegistry = agentRegistry;
    this.settings = settings;
    this.requestHost = requestHost;
    this.materializer = materializer;
  }

  AbstractA2aEndpoint endpoint() {
    return endpoint;
  }

  /** Makes the context and task id of the request available to the endpoint. */
  void bind(String contextId, String taskId) {
    requestContext.bind(contextId, taskId);
    A2aTracing.set(span, A2aTracing.CONTEXT_ID, contextId);
    A2aTracing.set(span, A2aTracing.TASK_ID, taskId);
  }

  // ---------------------------------------------------------------------------------------------
  // Tracing

  /**
   * Answers the request inside a span for the A2A method, a child of the request's span. The span
   * ends with the response, or with the stream for a streaming method.
   */
  private HttpResponse traced(String method, java.util.function.Supplier<HttpResponse> call) {
    span = httpContext.tracing().startSpan("A2A " + method);
    A2aTracing.set(span, A2aTracing.METHOD, method);
    A2aTracing.set(span, A2aTracing.CALLER_SUBJECT, callerSubject());
    try {
      return call.get();
    } catch (RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      if (!streamed) A2aTracing.end(span, failure);
    }
  }

  // The subject of the caller's token, when the request carries one
  private String callerSubject() {
    try {
      return httpContext.getJwtClaims().subject().orElse(null);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Records the task of a reply on the span. */
  <T> T traceReply(T reply) {
    A2aTracing.reply(span, reply);
    return reply;
  }

  /** Records a refused request on the span. */
  private void traceFailure(RuntimeException e) {
    failure = e;
  }

  /** Records each event on the span, and ends the span with the stream. */
  Source<A2aStreamResponse, akka.NotUsed> traceStream(
      Source<A2aStreamResponse, akka.NotUsed> events) {
    streamed = true;
    var traced = span;
    return events
        .map(
            event -> {
              A2aTracing.event(traced, event);
              return event;
            })
        .watchTermination(
            (notUsed, done) -> {
              done.whenComplete((d, error) -> A2aTracing.end(traced, error));
              return akka.NotUsed.getInstance();
            });
  }

  A2aTaskWorkflows taskWorkflows() {
    return new A2aTaskWorkflows(componentClient, settings);
  }

  <T> T readBody(HttpEntity.Strict body, Class<T> type) {
    JsonNode tree;
    try {
      tree = mapper.readTree(body.getData().utf8String());
    } catch (JsonProcessingException e) {
      throw new A2aException(A2aErrorCode.JSON_PARSE_ERROR, "The request is not valid JSON");
    }
    if (tree == null || !tree.isObject()) {
      throw new A2aException(A2aErrorCode.INVALID_PARAMS, "The request body is not a JSON object");
    }
    return read(tree, type, A2aErrorCode.INVALID_PARAMS);
  }

  /** The query parameter of the HTTP request, if present. */
  Optional<String> queryParam(String name) {
    return httpContext.queryParams().getString(name);
  }

  // A stream that fails before it starts is answered as an ordinary HTTP+JSON error.
  private HttpResponse httpStream(String method, java.util.function.Supplier<HttpResponse> call) {
    return traced(
        method,
        () -> {
          try {
            return call.get();
          } catch (A2aException e) {
            traceFailure(e);
            return httpError(e);
          } catch (RuntimeException e) {
            log.error("A2A request failed", e);
            traceFailure(e);
            return httpError(new A2aException(A2aErrorCode.INTERNAL_ERROR, "Internal error"));
          }
        });
  }

  // ---------------------------------------------------------------------------------------------
  // Routes

  public HttpResponse jsonRpc(HttpEntity.Strict body) {
    JsonNode id = null;
    try {
      JsonNode tree;
      try {
        tree = mapper.readTree(body.getData().utf8String());
      } catch (JsonProcessingException e) {
        throw new A2aException(A2aErrorCode.JSON_PARSE_ERROR, "The request is not valid JSON");
      }
      if (tree == null || !tree.isObject()) {
        throw new A2aException(
            A2aErrorCode.INVALID_REQUEST, "The request is not a JSON-RPC request object");
      }
      var idNode = tree.get("id");
      if (idNode != null && (idNode.isTextual() || idNode.isNumber())) id = idNode;
      var request = read(tree, JsonRpcRequest.class, A2aErrorCode.INVALID_REQUEST);
      if (!request.valid() || (request.params() != null && !request.params().isObject())) {
        throw new A2aException(
            A2aErrorCode.INVALID_REQUEST, "The request is not a JSON-RPC 2.0 request");
      }
      var rpcId = id;
      return traced(request.method(), () -> jsonRpcMethod(request, rpcId));
    } catch (A2aException e) {
      return response(
          200, ContentTypes.APPLICATION_JSON, JsonRpcResponse.failure(id, jsonRpcError(e)));
    } catch (RuntimeException e) {
      log.error("A2A request failed", e);
      var error = new A2aException(A2aErrorCode.INTERNAL_ERROR, "Internal error");
      return response(
          200, ContentTypes.APPLICATION_JSON, JsonRpcResponse.failure(id, jsonRpcError(error)));
    }
  }

  private HttpResponse jsonRpcMethod(JsonRpcRequest request, JsonNode id) {
    try {
      checkVersion();
      JsonNode params = request.params() == null ? mapper.createObjectNode() : request.params();
      var streamedResponse = streaming.jsonRpc(request.method(), id, params);
      if (streamedResponse.isPresent()) return streamedResponse.get();
      JsonNode result =
          switch (request.method()) {
            case A2aMethods.LIST_TASKS ->
                mapper.valueToTree(
                    operations.list(
                        read(params, A2aListTasksRequest.class, A2aErrorCode.INVALID_PARAMS)));
            case A2aMethods.CANCEL_TASK ->
                mapper.valueToTree(
                    traceReply(
                        operations.cancel(
                            read(
                                params, A2aCancelTaskRequest.class, A2aErrorCode.INVALID_PARAMS))));
            case A2aMethods.SEND_MESSAGE ->
                mapper.valueToTree(
                    traceReply(
                        send(
                            read(
                                params,
                                A2aSendMessageRequest.class,
                                A2aErrorCode.INVALID_PARAMS))));
            case A2aMethods.GET_TASK ->
                mapper.valueToTree(
                    traceReply(
                        getTask(
                            read(params, A2aGetTaskRequest.class, A2aErrorCode.INVALID_PARAMS))));
            case A2aMethods.CREATE_TASK_PUSH_NOTIFICATION_CONFIG,
                A2aMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG,
                A2aMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIGS,
                A2aMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG ->
                pushConfigMethods().jsonRpc(request.method(), params);
            default -> throw unsupported(request.method());
          };
      return response(200, ContentTypes.APPLICATION_JSON, JsonRpcResponse.success(id, result));
    } catch (A2aException e) {
      traceFailure(e);
      return response(
          200, ContentTypes.APPLICATION_JSON, JsonRpcResponse.failure(id, jsonRpcError(e)));
    } catch (RuntimeException e) {
      log.error("A2A request failed", e);
      traceFailure(e);
      var error = new A2aException(A2aErrorCode.INTERNAL_ERROR, "Internal error");
      return response(
          200, ContentTypes.APPLICATION_JSON, JsonRpcResponse.failure(id, jsonRpcError(error)));
    }
  }

  public HttpResponse sendMessage(HttpEntity.Strict body) {
    return httpJson(
        A2aMethods.SEND_MESSAGE,
        () -> {
          checkVersion();
          JsonNode tree;
          try {
            tree = mapper.readTree(body.getData().utf8String());
          } catch (JsonProcessingException e) {
            throw new A2aException(A2aErrorCode.JSON_PARSE_ERROR, "The request is not valid JSON");
          }
          if (tree == null || !tree.isObject()) {
            throw new A2aException(
                A2aErrorCode.INVALID_PARAMS, "The request body is not a JSON object");
          }
          return send(read(tree, A2aSendMessageRequest.class, A2aErrorCode.INVALID_PARAMS));
        });
  }

  public HttpResponse getTask(String taskId) {
    if (taskId.endsWith(SUBSCRIBE_SUFFIX)) return subscribeToTask(withoutSuffix(taskId));
    return httpJson(
        A2aMethods.GET_TASK,
        () -> {
          checkVersion();
          Integer historyLength =
              httpContext
                  .queryParams()
                  .getString("historyLength")
                  .map(
                      value -> {
                        try {
                          return Integer.valueOf(value);
                        } catch (NumberFormatException e) {
                          throw invalidParams("historyLength must be an integer");
                        }
                      })
                  .orElse(null);
          return getTask(new A2aGetTaskRequest(null, taskId, historyLength));
        });
  }

  public HttpResponse sendStreamingMessage(HttpEntity.Strict body) {
    return httpStream(
        A2aMethods.SEND_STREAMING_MESSAGE,
        () -> {
          checkVersion();
          return streaming.sendStreamingMessage(
              readBody(body, A2aSendMessageRequest.class), r -> r);
        });
  }

  /** The routes {@code POST /tasks/{id}:cancel} and {@code POST /tasks/{id}:subscribe}. */
  public HttpResponse taskAction(String taskIdAndAction) {
    if (taskIdAndAction.endsWith(CANCEL_SUFFIX)) {
      return cancelTask(
          taskIdAndAction.substring(0, taskIdAndAction.length() - CANCEL_SUFFIX.length()));
    }
    if (taskIdAndAction.endsWith(SUBSCRIBE_SUFFIX))
      return subscribeToTask(withoutSuffix(taskIdAndAction));
    return httpJson(
        "unknown",
        () -> {
          throw new A2aException(A2aErrorCode.METHOD_NOT_FOUND, "Method not found");
        });
  }

  private static String withoutSuffix(String taskIdAndAction) {
    return taskIdAndAction.substring(0, taskIdAndAction.lastIndexOf(':'));
  }

  private HttpResponse subscribeToTask(String taskId) {
    return httpStream(
        A2aMethods.SUBSCRIBE_TO_TASK,
        () -> {
          checkVersion();
          return streaming.subscribeToTask(taskId, r -> r);
        });
  }

  public HttpResponse listTasks() {
    return httpJson(
        A2aMethods.LIST_TASKS,
        () -> {
          checkVersion();
          return operations.list(operations.listRequestFromQuery());
        });
  }

  public HttpResponse cancelTask(String taskId) {
    return httpJson(
        A2aMethods.CANCEL_TASK,
        () -> {
          checkVersion();
          return operations.cancel(new A2aCancelTaskRequest(null, taskId, null));
        });
  }

  /** An HTTP+JSON route of a method that this agent does not support. */
  public HttpResponse unsupportedMethod(String method) {
    return httpJson(
        method,
        () -> {
          checkVersion();
          throw unsupported(method);
        });
  }

  /**
   * The Agent Card, built for each request so that it follows the host the client used, and signed
   * when a signing key is configured. A request whose {@code If-None-Match} names the current
   * {@code ETag} is answered 304 without the card.
   */
  public HttpResponse agentCard() {
    byte[] bytes;
    String etag;
    try {
      var card = buildAgentCard();
      // The tag is taken before signing, because an ECDSA signature differs each time
      var unsigned = mapper.writeValueAsBytes(card);
      etag = etag(unsigned, settings.signer().map(AgentCardSigning.Signer::keyId).orElse(""));
      bytes =
          settings.signer().isPresent()
              ? mapper.writeValueAsBytes(AgentCardSigning.sign(card, settings.signer().get()))
              : unsigned;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to write the Agent Card", e);
    }
    var cacheControl = RawHeader.create("Cache-Control", CARD_CACHE_CONTROL);
    var notModified =
        httpContext
            .requestHeader("If-None-Match")
            .map(HttpHeader::value)
            .filter(
                value ->
                    Arrays.stream(value.split(","))
                        .map(String::trim)
                        .anyMatch(tag -> tag.equals(etag) || ("W/" + tag).equals(etag)))
            .isPresent();
    if (notModified) {
      return HttpResponse.create()
          .withStatus(StatusCodes.NOT_MODIFIED)
          .addHeader(RawHeader.create("ETag", etag))
          .addHeader(cacheControl);
    }
    return HttpResponse.create()
        .withEntity(ContentTypes.APPLICATION_JSON, bytes)
        .addHeader(RawHeader.create("ETag", etag))
        .addHeader(cacheControl);
  }

  // A weak entity tag of the card content and the key that signs it: a signed card is the same
  // card each time, although its signature bytes differ
  private static String etag(byte[] card, String keyId) {
    try {
      var sha = MessageDigest.getInstance("SHA-256");
      sha.update(card);
      var digest = sha.digest(keyId.getBytes(StandardCharsets.UTF_8));
      return "W/\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest) + "\"";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Methods

  /** A message on a task that exists, which only a task workflow accepts. */
  record Continuation(A2aTask task, A2aMessage message) implements Prepared {}

  /** A message that starts a new task. */
  record NewTask(A2aSkillTable.Entry skill, A2aMessage userMessage, Object input)
      implements Prepared {}

  /** A checked {@code SendMessage} or {@code SendStreamingMessage} request. */
  sealed interface Prepared permits Continuation, NewTask {}

  /**
   * Checks a {@code SendMessage} or {@code SendStreamingMessage} request, and finds the task it
   * continues, or the skill and the ids of the task it starts.
   */
  Prepared prepare(A2aSendMessageRequest request) {
    var message = request.message();
    if (message == null) throw invalidParams("The request has no message");
    if (isBlank(message.messageId())) throw invalidParams("The message has no messageId");
    if (message.parts().isEmpty()) throw invalidParams("The message has no parts");
    if (message.role() == A2aRole.AGENT) throw invalidParams("The message has the role ROLE_AGENT");
    checkHistoryLength(historyLength(request));

    if (!isBlank(message.taskId())) {
      var existing = lookup(message.taskId());
      if (!isBlank(message.contextId()) && !message.contextId().equals(existing.contextId())) {
        throw invalidParams(
            "The contextId of the message does not match the context of task ["
                + message.taskId()
                + "]");
      }
      return new Continuation(existing, message);
    }

    var contextId =
        isBlank(message.contextId()) ? UUID.randomUUID().toString() : message.contextId();
    var taskId = UUID.randomUUID().toString();
    bind(contextId, taskId);
    var skill = selectSkill(message, request.metadata());
    A2aTracing.set(span, A2aTracing.SKILL_ID, skill.skill().id());
    var input = agentInput(skill, message);
    var userMessage = message.withContextId(contextId).withTaskId(taskId).withRole(A2aRole.USER);
    return new NewTask(skill, userMessage, input);
  }

  static Integer historyLength(A2aSendMessageRequest request) {
    return request.configuration() == null ? null : request.configuration().historyLength();
  }

  /** The error for a message on a task that does not accept one. */
  static A2aException notAccepting(A2aTask task) {
    return new A2aException(
        A2aErrorCode.UNSUPPORTED_OPERATION,
        task.status().state().terminal()
            ? "Task [" + task.id() + "] has ended and accepts no more messages"
            : "Task [" + task.id() + "] is not waiting for input");
  }

  /** Records the new task of a short skill, and returns it in the working state. */
  A2aTask startShortTask(NewTask newTask) {
    var userMessage = newTask.userMessage();
    var submitted =
        new A2aTask(
            userMessage.taskId(),
            userMessage.contextId(),
            status(A2aTaskState.SUBMITTED),
            null,
            List.of(userMessage),
            null);
    componentClient
        .forKeyValueEntity(submitted.id())
        .method(A2aTaskEntity::create)
        .invoke(new A2aTaskEntity.Create(submitted, newTask.skill().skill().id(), owner()));
    return store
        .change(submitted.id(), A2aTaskEntity.Change.status(A2aTaskState.WORKING, null))
        .orElseThrow()
        .task();
  }

  private A2aSendMessageResponse send(A2aSendMessageRequest request) {
    var configuration = request.configuration();
    var historyLength = historyLength(request);
    return switch (prepare(request)) {
      case Continuation continuation ->
          taskWorkflows()
              .continueTask(
                  skillTable(), continuation.task(), continuation.message(), configuration)
              .orElseThrow(() -> notAccepting(continuation.task()));
      case NewTask newTask when newTask.skill().taskWorkflow() != null ->
          taskWorkflows().start(newTask.skill(), newTask.userMessage(), owner(), configuration);
      case NewTask newTask -> {
        var working = startShortTask(newTask);
        var artifactId = UUID.randomUUID().toString();
        var answer = callAgent(newTask.skill(), working.contextId(), newTask.input());
        if (configuration != null && configuration.returnImmediately()) {
          answer.whenComplete(
              (result, error) -> {
                try {
                  record(working, outcome(working, result, error, artifactId));
                } catch (RuntimeException e) {
                  log.error("Failed to record the outcome of A2A task [{}]", working.id(), e);
                }
              });
          yield A2aSendMessageResponse.of(trimHistory(working, historyLength));
        }
        var change =
            answer
                .handle((result, error) -> outcome(working, result, error, artifactId))
                .toCompletableFuture()
                .join();
        yield A2aSendMessageResponse.of(trimHistory(record(working, change), historyLength));
      }
    };
  }

  private A2aTask getTask(A2aGetTaskRequest request) {
    if (isBlank(request.id())) throw invalidParams("The request has no task id");
    checkHistoryLength(request.historyLength());
    var task = lookup(request.id());
    bind(task.contextId(), task.id());
    return trimHistory(task, request.historyLength());
  }

  // ---------------------------------------------------------------------------------------------
  // Tasks and agents

  /** A task the caller may see, or TASK_NOT_FOUND. A task of another subject is not revealed. */
  A2aTask lookup(String taskId) {
    var state =
        componentClient.forKeyValueEntity(taskId).method(A2aTaskEntity::get).invoke().state();
    if (state == null || (state.owner() != null && !state.owner().equals(owner()))) {
      throw new A2aException(A2aErrorCode.TASK_NOT_FOUND, "Task [" + taskId + "] not found");
    }
    return state.task();
  }

  /**
   * Records the outcome of a short skill, and returns the task. A task that was canceled meanwhile
   * stays canceled.
   */
  private A2aTask record(A2aTask working, A2aTaskEntity.Change change) {
    return store
        .change(working.id(), change)
        .or(() -> store.get(working.id()))
        .orElseThrow()
        .task();
  }

  A2aSkillTable skillTable() {
    return A2aSkillTable.of(endpoint._internalAgents(), agentRegistry);
  }

  private A2aPushConfigMethods pushConfigMethods() {
    return new A2aPushConfigMethods(
        componentClient,
        settings.pushUrls(),
        skillTable().hasTaskWorkflows(),
        this::lookup,
        mapper);
  }

  /** An HTTP+JSON route of the push notification configuration methods. */
  public HttpResponse pushConfigRoute(
      String method, String taskId, String configId, HttpEntity.Strict body) {
    return httpJson(
        method,
        () -> {
          checkVersion();
          return pushConfigMethods().http(method, taskId, configId, body, httpContext);
        });
  }

  // Tasks are scoped to the JWT subject when the endpoint requires a token.
  String owner() {
    if (jwt == null) return null;
    try {
      return httpContext.getJwtClaims().subject().orElse(null);
    } catch (RuntimeException e) {
      return null;
    }
  }

  A2aSkillTable.Entry selectSkill(A2aMessage message, Map<String, Object> metadata) {
    var table = skillTable();
    var requested =
        stringValue(message.metadata(), SKILL_ID).or(() -> stringValue(metadata, SKILL_ID));
    if (requested.isPresent()) {
      return table
          .find(requested.get())
          .orElseThrow(
              () ->
                  invalidParams(
                      "Unknown skill ["
                          + requested.get()
                          + "]. The skills are: "
                          + String.join(", ", table.ids())));
    }
    return table.first().orElseThrow(() -> invalidParams("This agent publishes no skills"));
  }

  Object agentInput(A2aSkillTable.Entry skill, A2aMessage message) {
    if (skill.inputType() == null) return null;
    var text =
        message.parts().stream()
            .map(p -> p instanceof A2aPart.Text part ? part.text() : null)
            .filter(t -> t != null)
            .collect(Collectors.joining("\n"));
    var data =
        message.parts().stream()
            .filter(p -> p instanceof A2aPart.Data)
            .map(p -> ((A2aPart.Data) p).data())
            .findFirst();
    if (skill.inputType() == String.class) {
      if (text.isEmpty() && data.isPresent()) {
        try {
          return mapper.writeValueAsString(data.get());
        } catch (JsonProcessingException e) {
          throw invalidParams("The data part cannot be read");
        }
      }
      return text;
    }
    var type = mapper.constructType(skill.inputType());
    try {
      return data.isPresent()
          ? mapper.convertValue(data.get(), type)
          : mapper.readValue(text, type);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw invalidParams(
          "Skill ["
              + skill.skill().id()
              + "] expects a data part, or text with JSON, of type "
              + type.getRawClass().getSimpleName());
    }
  }

  CompletionStage<Object> callAgent(A2aSkillTable.Entry skill, String contextId, Object input) {
    DynamicMethodRef<Object, Object> call =
        componentClient.forAgent().inSession(contextId).dynamicCall(skill.agentId());
    try {
      return skill.inputType() == null ? call.invokeAsync() : call.invokeAsync(input);
    } catch (RuntimeException e) {
      return java.util.concurrent.CompletableFuture.failedFuture(e);
    }
  }

  /** The change that records the answer of an agent, or its failure, in the working task. */
  A2aTaskEntity.Change outcome(A2aTask working, Object result, Throwable error, String artifactId) {
    if (error != null) {
      log.warn("The agent failed to answer A2A task [{}]", working.id(), error);
      var reply =
          agentMessage(working, List.of(A2aPart.text("The agent could not answer the message.")));
      return A2aTaskEntity.Change.status(A2aTaskState.FAILED, reply);
    }
    var parts =
        List.<A2aPart>of(
            result instanceof String text
                ? A2aPart.text(text)
                : result == null
                    ? A2aPart.text("")
                    : A2aPart.data(mapper.convertValue(result, Object.class)));
    var artifact = new A2aArtifact(artifactId, "response", null, parts, null, null);
    return new A2aTaskEntity.Change(
        agentMessage(working, parts), artifact, A2aTaskState.COMPLETED, null, false);
  }

  static A2aMessage agentMessage(A2aTask task, List<A2aPart> parts) {
    return new A2aMessage(
        UUID.randomUUID().toString(),
        task.contextId(),
        task.id(),
        A2aRole.AGENT,
        parts,
        null,
        null,
        null);
  }

  static A2aTaskStatus status(A2aTaskState state) {
    return new A2aTaskStatus(state, null, Instant.now());
  }

  static A2aTask withStatus(A2aTask task, A2aTaskStatus status) {
    return new A2aTask(
        task.id(), task.contextId(), status, task.artifacts(), task.history(), task.metadata());
  }

  static A2aTask trimHistory(A2aTask task, Integer historyLength) {
    if (historyLength == null || task.history().size() <= historyLength) return task;
    var history = task.history();
    return new A2aTask(
        task.id(),
        task.contextId(),
        task.status(),
        task.artifacts(),
        history.subList(history.size() - historyLength, history.size()),
        task.metadata());
  }

  // ---------------------------------------------------------------------------------------------
  // Agent Card

  private A2aAgentCard buildAgentCard() {
    var table = skillTable();
    var skills = table.skills();
    var url = baseUrl() + endpointPath();
    var interfaces =
        List.of(
            new A2aAgentCard.Interface(url, "JSONRPC", null, PROTOCOL_VERSION),
            new A2aAgentCard.Interface(url, "HTTP+JSON", null, PROTOCOL_VERSION));
    var schemes = securitySchemes();
    var requirements =
        schemes.keySet().stream()
            .map(
                name ->
                    new A2aSecurityRequirement(
                        Map.of(name, new A2aSecurityRequirement.Scopes(List.of()))))
            .toList();
    return new A2aAgentCard(
        annotation.name(),
        annotation.description().isBlank() ? annotation.name() : annotation.description(),
        interfaces,
        null,
        annotation.version(),
        null,
        new A2aAgentCard.Capabilities(true, table.hasTaskWorkflows(), null, null),
        schemes,
        requirements,
        DEFAULT_INPUT_MODES,
        DEFAULT_OUTPUT_MODES,
        skills,
        null,
        null);
  }

  // Bearer issuers that are URLs become OpenID Connect schemes. Any other @JWT is a bearer scheme.
  private Map<String, A2aSecurityScheme> securitySchemes() {
    var schemes = new LinkedHashMap<String, A2aSecurityScheme>();
    if (jwt == null || !List.of(jwt.validate()).contains(JWT.JwtMethodMode.BEARER_TOKEN)) {
      return schemes;
    }
    var urlIssuers =
        List.of(jwt.bearerTokenIssuers()).stream()
            .filter(i -> i.startsWith("https://") || i.startsWith("http://"))
            .toList();
    for (int i = 0; i < urlIssuers.size(); i++) {
      var issuer = urlIssuers.get(i);
      var discovery =
          (issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer)
              + "/.well-known/openid-configuration";
      schemes.put(
          urlIssuers.size() == 1 ? "oidc" : "oidc-" + (i + 1),
          new A2aSecurityScheme.OpenIdConnect(null, discovery));
    }
    if (urlIssuers.size() < jwt.bearerTokenIssuers().length || urlIssuers.isEmpty()) {
      schemes.put("bearer", new A2aSecurityScheme.HttpAuth(null, "Bearer", "JWT"));
    }
    return schemes;
  }

  private String baseUrl() {
    String base;
    if (!annotation.publicUrl().isBlank()) base = annotation.publicUrl();
    else if (!settings.publicUrl().isBlank()) base = settings.publicUrl();
    else {
      var scheme =
          httpContext
              .requestHeader("X-Forwarded-Proto")
              .map(HttpHeader::value)
              .filter(v -> v.equals("https") || v.equals("http"))
              .orElse("http");
      base = scheme + "://" + requestHost.orElse("localhost");
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  private String endpointPath() {
    var path = annotation.path().startsWith("/") ? annotation.path() : "/" + annotation.path();
    return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  // ---------------------------------------------------------------------------------------------
  // Protocol details

  // A request without A2A-Version is served as 1.0. A patch version is ignored.
  private void checkVersion() {
    var version = httpContext.requestHeader(VERSION_HEADER).map(HttpHeader::value).orElse("");
    if (version.isBlank()) return;
    var parts = version.trim().split("\\.");
    var majorMinor = parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0];
    if (!majorMinor.equals(PROTOCOL_VERSION) && !majorMinor.equals("1")) {
      throw new A2aException(
          A2aErrorCode.VERSION_NOT_SUPPORTED,
          "A2A version [" + version + "] is not supported. Supported: " + PROTOCOL_VERSION);
    }
  }

  static void checkHistoryLength(Integer historyLength) {
    if (historyLength != null && historyLength < 0) {
      throw invalidParams("historyLength must not be negative");
    }
  }

  private static A2aException unsupported(String method) {
    return switch (method) {
      case A2aMethods.CREATE_TASK_PUSH_NOTIFICATION_CONFIG,
          A2aMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG,
          A2aMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIGS,
          A2aMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG ->
          new A2aException(
              A2aErrorCode.PUSH_NOTIFICATION_NOT_SUPPORTED,
              "This agent does not support push notifications");
      case A2aMethods.GET_EXTENDED_AGENT_CARD ->
          new A2aException(
              A2aErrorCode.UNSUPPORTED_OPERATION, "This agent has no extended Agent Card");
      default -> new A2aException(A2aErrorCode.METHOD_NOT_FOUND, "Method not found: " + method);
    };
  }

  <T> T read(JsonNode node, Class<T> type, A2aErrorCode code) {
    try {
      return mapper.treeToValue(A2aJsonNames.withJsonNames(node), type);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      var detail = e instanceof JsonProcessingException j ? j.getOriginalMessage() : e.getMessage();
      throw new A2aException(code, "Invalid parameters: " + detail);
    }
  }

  private static Optional<String> stringValue(Map<String, Object> map, String key) {
    if (map == null) return Optional.empty();
    return map.get(key) instanceof String s && !s.isBlank() ? Optional.of(s) : Optional.empty();
  }

  static A2aException invalidParams(String message) {
    return new A2aException(A2aErrorCode.INVALID_PARAMS, message);
  }

  static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private JsonRpcError jsonRpcError(A2aException e) {
    return new JsonRpcError(
        e.code().code(), e.getMessage(), e.code().a2aSpecific() ? errorDetails(e.code()) : null);
  }

  private ArrayNode errorDetails(A2aErrorCode code) {
    ObjectNode info = mapper.createObjectNode();
    info.put("@type", ERROR_INFO_TYPE);
    info.put("reason", code.reason());
    info.put("domain", A2aErrorCode.ERROR_DOMAIN);
    return mapper.createArrayNode().add(info);
  }

  private interface Call {
    Object run();
  }

  private HttpResponse httpJson(String method, Call call) {
    return traced(
        method,
        () -> {
          try {
            return response(200, ContentTypes.APPLICATION_JSON, traceReply(call.run()));
          } catch (A2aException e) {
            traceFailure(e);
            return httpError(e);
          } catch (RuntimeException e) {
            log.error("A2A request failed", e);
            traceFailure(e);
            return httpError(new A2aException(A2aErrorCode.INTERNAL_ERROR, "Internal error"));
          }
        });
  }

  // The google.rpc.Status form of the HTTP+JSON binding.
  HttpResponse httpError(A2aException e) {
    int status = httpStatus(e.code());
    ObjectNode error = mapper.createObjectNode();
    error.put("code", status);
    error.put("status", rpcStatus(e.code()));
    error.put("message", e.getMessage());
    if (e.code().a2aSpecific()) error.set("details", errorDetails(e.code()));
    ObjectNode body = mapper.createObjectNode();
    body.set("error", error);
    return response(status, ContentTypes.APPLICATION_JSON, body);
  }

  private static int httpStatus(A2aErrorCode code) {
    return switch (code) {
      case TASK_NOT_FOUND, METHOD_NOT_FOUND -> 404;
      case INTERNAL_ERROR, INVALID_AGENT_RESPONSE -> 500;
      default -> 400;
    };
  }

  private static String rpcStatus(A2aErrorCode code) {
    return switch (code) {
      case TASK_NOT_FOUND, METHOD_NOT_FOUND -> "NOT_FOUND";
      case INTERNAL_ERROR, INVALID_AGENT_RESPONSE -> "INTERNAL";
      case JSON_PARSE_ERROR, INVALID_REQUEST, INVALID_PARAMS, CONTENT_TYPE_NOT_SUPPORTED ->
          "INVALID_ARGUMENT";
      default -> "FAILED_PRECONDITION";
    };
  }

  private HttpResponse response(int status, ContentType contentType, Object body) {
    try {
      return HttpResponse.create()
          .withStatus(status)
          .withEntity(contentType, mapper.writeValueAsBytes(body));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to write the A2A response", e);
    }
  }
}
