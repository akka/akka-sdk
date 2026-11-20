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
import akka.http.javadsl.model.headers.RawHeader;
import akka.javasdk.JsonSupport;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.a2a.A2aAgentCard;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aCancelTaskRequest;
import akka.javasdk.a2a.A2aGetTaskRequest;
import akka.javasdk.a2a.A2aListTasksRequest;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRequestContext;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aSecurityRequirement;
import akka.javasdk.a2a.A2aSecurityScheme;
import akka.javasdk.a2a.A2aSendMessageRequest;
import akka.javasdk.a2a.A2aSendMessageResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
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
  private final RequestContext httpContext;
  private final AgentRegistry agentRegistry;
  private final String configuredPublicUrl;
  private final Optional<String> requestHost;
  private final RequestContextImpl requestContext = new RequestContextImpl();
  private final A2aStreaming streaming = new A2aStreaming(this);
  private final A2aTaskOperations operations = new A2aTaskOperations(this);

  /**
   * @param configuredPublicUrl the setting {@code akka.javasdk.a2a.endpoint.public-url}, empty when
   *     not set
   * @param requestHost the {@code Host} header of the request
   */
  public A2aRequestHandler(
      AbstractA2aEndpoint endpoint,
      ComponentClient componentClient,
      RequestContext httpContext,
      AgentRegistry agentRegistry,
      String configuredPublicUrl,
      Optional<String> requestHost) {
    this.endpoint = endpoint;
    this.annotation = endpoint.getClass().getAnnotation(A2aEndpoint.class);
    this.jwt = endpoint.getClass().getAnnotation(JWT.class);
    this.componentClient = componentClient;
    this.httpContext = httpContext;
    this.agentRegistry = agentRegistry;
    this.configuredPublicUrl = configuredPublicUrl;
    this.requestHost = requestHost;
  }

  public AbstractA2aEndpoint endpoint() {
    return endpoint;
  }

  public A2aRequestContext requestContext() {
    return requestContext;
  }

  /** Makes the context and task id of the request available to the endpoint. */
  void bind(String contextId, String taskId) {
    requestContext.contextId = contextId;
    requestContext.taskId = taskId;
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
  private HttpResponse httpStream(java.util.function.Supplier<HttpResponse> call) {
    try {
      return call.get();
    } catch (A2aException e) {
      return httpError(e);
    } catch (RuntimeException e) {
      log.error("A2A request failed", e);
      return httpError(new A2aException(A2aErrorCode.INTERNAL_ERROR, "Internal error"));
    }
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
      checkVersion();
      JsonNode params = request.params() == null ? mapper.createObjectNode() : request.params();
      var streamed = streaming.jsonRpc(request.method(), id, params);
      if (streamed.isPresent()) return streamed.get();
      JsonNode result =
          switch (request.method()) {
            case A2aMethods.LIST_TASKS ->
                mapper.valueToTree(
                    operations.list(
                        read(params, A2aListTasksRequest.class, A2aErrorCode.INVALID_PARAMS)));
            case A2aMethods.CANCEL_TASK ->
                mapper.valueToTree(
                    operations.cancel(
                        read(params, A2aCancelTaskRequest.class, A2aErrorCode.INVALID_PARAMS)));
            case A2aMethods.SEND_MESSAGE ->
                mapper.valueToTree(
                    send(read(params, A2aSendMessageRequest.class, A2aErrorCode.INVALID_PARAMS)));
            case A2aMethods.GET_TASK ->
                mapper.valueToTree(
                    getTask(read(params, A2aGetTaskRequest.class, A2aErrorCode.INVALID_PARAMS)));
            case A2aMethods.CREATE_TASK_PUSH_NOTIFICATION_CONFIG,
                A2aMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG,
                A2aMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIGS,
                A2aMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG ->
                pushConfigMethods().jsonRpc(request.method(), params);
            default -> throw unsupported(request.method());
          };
      return response(200, ContentTypes.APPLICATION_JSON, JsonRpcResponse.success(id, result));
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

  public HttpResponse sendMessage(HttpEntity.Strict body) {
    return httpJson(
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
        () -> {
          throw new A2aException(A2aErrorCode.METHOD_NOT_FOUND, "Method not found");
        });
  }

  private static String withoutSuffix(String taskIdAndAction) {
    return taskIdAndAction.substring(0, taskIdAndAction.lastIndexOf(':'));
  }

  private HttpResponse subscribeToTask(String taskId) {
    return httpStream(
        () -> {
          checkVersion();
          return streaming.subscribeToTask(taskId, r -> r);
        });
  }

  public HttpResponse listTasks() {
    return httpJson(
        () -> {
          checkVersion();
          return operations.list(operations.listRequestFromQuery());
        });
  }

  public HttpResponse cancelTask(String taskId) {
    return httpJson(
        () -> {
          checkVersion();
          return operations.cancel(new A2aCancelTaskRequest(null, taskId, null));
        });
  }

  /** An HTTP+JSON route of a method that this agent does not support. */
  public HttpResponse unsupportedMethod(String method) {
    return httpJson(
        () -> {
          checkVersion();
          throw unsupported(method);
        });
  }

  /** The Agent Card, built for each request so that it follows the host the client used. */
  public HttpResponse agentCard() {
    try {
      var bytes = mapper.writeValueAsBytes(buildAgentCard());
      return HttpResponse.create()
          .withEntity(ContentTypes.APPLICATION_JSON, bytes)
          .addHeader(RawHeader.create("Cache-Control", CARD_CACHE_CONTROL));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to write the Agent Card", e);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Methods

  private A2aSendMessageResponse send(A2aSendMessageRequest request) {
    var message = request.message();
    if (message == null) throw invalidParams("The request has no message");
    if (isBlank(message.messageId())) throw invalidParams("The message has no messageId");
    if (message.parts().isEmpty()) throw invalidParams("The message has no parts");
    if (message.role() == A2aRole.AGENT) throw invalidParams("The message has the role ROLE_AGENT");
    var configuration = request.configuration();
    Integer historyLength = configuration == null ? null : configuration.historyLength();
    checkHistoryLength(historyLength);

    if (!isBlank(message.taskId())) {
      var existing = lookup(message.taskId());
      if (!isBlank(message.contextId()) && !message.contextId().equals(existing.contextId())) {
        throw invalidParams(
            "The contextId of the message does not match the context of task ["
                + message.taskId()
                + "]");
      }
      var continued =
          new A2aTaskWorkflows(componentClient)
              .continueTask(skillTable(), existing, message, configuration);
      if (continued.isPresent()) return continued.get();
      var state = existing.status().state();
      throw new A2aException(
          A2aErrorCode.UNSUPPORTED_OPERATION,
          state.terminal()
              ? "Task [" + message.taskId() + "] has ended and accepts no more messages"
              : "Task [" + message.taskId() + "] is not waiting for input");
    }

    var contextId =
        isBlank(message.contextId()) ? UUID.randomUUID().toString() : message.contextId();
    var taskId = UUID.randomUUID().toString();
    requestContext.contextId = contextId;
    requestContext.taskId = taskId;

    var skill = selectSkill(message, request.metadata());
    var input = agentInput(skill, message);

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
    if (skill.taskWorkflow() != null) {
      return new A2aTaskWorkflows(componentClient)
          .start(skill, userMessage, owner(), configuration);
    }
    var submitted =
        new A2aTask(
            taskId, contextId, status(A2aTaskState.SUBMITTED), null, List.of(userMessage), null);
    componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskEntity::create)
        .invoke(new A2aTaskEntity.Create(submitted, skill.skill().id(), owner()));
    var working = withStatus(submitted, status(A2aTaskState.WORKING));
    store(working);

    var answer = callAgent(skill, contextId, input);
    if (configuration != null && configuration.returnImmediately()) {
      answer.whenComplete(
          (result, error) -> {
            try {
              store(finish(working, result, error));
            } catch (RuntimeException e) {
              log.error("Failed to record the outcome of A2A task [{}]", taskId, e);
            }
          });
      return A2aSendMessageResponse.of(trimHistory(working, historyLength));
    }
    var finished =
        answer
            .handle((result, error) -> finish(working, result, error))
            .toCompletableFuture()
            .join();
    var stored = store(finished);
    return A2aSendMessageResponse.of(trimHistory(stored, historyLength));
  }

  private A2aTask getTask(A2aGetTaskRequest request) {
    if (isBlank(request.id())) throw invalidParams("The request has no task id");
    checkHistoryLength(request.historyLength());
    var task = lookup(request.id());
    requestContext.taskId = task.id();
    requestContext.contextId = task.contextId();
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

  A2aTask store(A2aTask task) {
    var stored =
        componentClient.forKeyValueEntity(task.id()).method(A2aTaskEntity::update).invoke(task);
    A2aPushNotifier.taskChanged(componentClient, stored);
    return stored.task();
  }

  A2aSkillTable skillTable() {
    return A2aSkillTable.of(endpoint._internalAgents(), agentRegistry);
  }

  private A2aPushConfigMethods pushConfigMethods() {
    return new A2aPushConfigMethods(
        componentClient, skillTable().hasTaskWorkflows(), this::lookup, mapper);
  }

  /** An HTTP+JSON route of the push notification configuration methods. */
  public HttpResponse pushConfigRoute(
      String method, String taskId, String configId, HttpEntity.Strict body) {
    return httpJson(
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
    var table = A2aSkillTable.of(endpoint._internalAgents(), agentRegistry);
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
    return table
        .single()
        .orElseThrow(
            () ->
                invalidParams(
                    "Select a skill with the message metadata field skillId. The skills are: "
                        + String.join(", ", table.ids())));
  }

  Object agentInput(A2aSkillTable.Entry skill, A2aMessage message) {
    if (skill.inputType() == null) return null;
    var text =
        message.parts().stream()
            .map(p -> p.text())
            .filter(t -> t != null)
            .collect(Collectors.joining("\n"));
    var data = message.parts().stream().map(p -> p.data()).filter(d -> d != null).findFirst();
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

  A2aTask finish(A2aTask working, Object result, Throwable error) {
    if (error != null) {
      log.warn("The agent failed to answer A2A task [{}]", working.id(), error);
      var reply =
          agentMessage(working, List.of(A2aPart.text("The agent could not answer the message.")));
      return withStatus(working, new A2aTaskStatus(A2aTaskState.FAILED, reply, Instant.now()));
    }
    var parts =
        List.of(
            result instanceof String text
                ? A2aPart.text(text)
                : result == null
                    ? A2aPart.text("")
                    : A2aPart.data(mapper.convertValue(result, Object.class)));
    var artifact =
        new A2aArtifact(UUID.randomUUID().toString(), "response", null, parts, null, null);
    var history = new ArrayList<>(working.history());
    history.add(agentMessage(working, parts));
    return new A2aTask(
        working.id(),
        working.contextId(),
        status(A2aTaskState.COMPLETED),
        List.of(artifact),
        history,
        working.metadata());
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
          A2aSecurityScheme.of(new A2aSecurityScheme.OpenIdConnect(null, discovery)));
    }
    if (urlIssuers.size() < jwt.bearerTokenIssuers().length || urlIssuers.isEmpty()) {
      schemes.put(
          "bearer", A2aSecurityScheme.of(new A2aSecurityScheme.HttpAuth(null, "Bearer", "JWT")));
    }
    return schemes;
  }

  private String baseUrl() {
    String base;
    if (!annotation.publicUrl().isBlank()) base = annotation.publicUrl();
    else if (!configuredPublicUrl.isBlank()) base = configuredPublicUrl;
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
      return mapper.treeToValue(node, type);
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

  private HttpResponse httpJson(Call call) {
    try {
      return response(200, ContentTypes.APPLICATION_JSON, call.run());
    } catch (A2aException e) {
      return httpError(e);
    } catch (RuntimeException e) {
      log.error("A2A request failed", e);
      return httpError(new A2aException(A2aErrorCode.INTERNAL_ERROR, "Internal error"));
    }
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

  private final class RequestContextImpl implements A2aRequestContext {
    private volatile String contextId;
    private volatile String taskId;

    @Override
    public Principals getPrincipals() {
      return httpContext.getPrincipals();
    }

    @Override
    public JwtClaims getJwtClaims() {
      return httpContext.getJwtClaims();
    }

    @Override
    public Optional<String> contextId() {
      return Optional.ofNullable(contextId);
    }

    @Override
    public Optional<String> taskId() {
      return Optional.ofNullable(taskId);
    }
  }
}
