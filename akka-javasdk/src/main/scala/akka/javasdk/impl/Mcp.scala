/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.impl.JsonRpc.JsonRpcErrorResponse
import akka.javasdk.impl.JsonRpc.JsonRpcRequest
import akka.javasdk.impl.JsonRpc.JsonRpcResponse
import akka.javasdk.impl.JsonRpc.JsonRpcSuccessResponse
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.`type`.TypeReference
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object Mcp {
  private val log = LoggerFactory.getLogger(classOf[Mcp.type])

  val ProtocolVersion = "2024-11-05"

  private val requestId = new AtomicLong(0)
  private val mapTypeRef: TypeReference[Map[String, AnyRef]] = new TypeReference[Map[String, AnyRef]] {}

  private def notification(method: String, params: Option[McpNotification]) =
    JsonRpc.JsonRpcRequest(method = method, params = params, id = None)

  def request(method: String, mcpRequest: Option[McpRequest]): JsonRpc.JsonRpcRequest =
    JsonRpc.JsonRpcRequest(method = method, params = mcpRequest, id = Some(requestId.incrementAndGet()))

  def result[T](response: JsonRpcResponse)(implicit ev: ClassTag[T]): T =
    response match {
      case JsonRpcSuccessResponse(_, _, payload) =>
        JsonRpc.Serialization.mapper.convertValue(payload, ev.runtimeClass).asInstanceOf[T]
      case error: JsonRpcErrorResponse =>
        throw new IllegalArgumentException(s"Cannot turn JSON-RPC error [$error] to MCP result")
    }

  def extractRequest[T](request: JsonRpc.JsonRpcRequest)(implicit ev: ClassTag[T]): T =
    JsonRpc.Serialization.mapper.convertValue(request.params.getOrElse(Map.empty), ev.runtimeClass).asInstanceOf[T]

  def toJsonRpc(requestId: Any, result: McpResult): JsonRpc.JsonRpcResponse = {
    val responseMap = JsonRpc.Serialization.mapper.convertValue(result, mapTypeRef)
    JsonRpcSuccessResponse(id = Some(requestId), result = responseMap)
  }

  def jsonRpcHandler[T: ClassTag](
      handler: T => Future[Option[McpResult]]): JsonRpcRequest => Future[Option[JsonRpcResponse]] = { jsonRpcRequest =>
    val mcpRequest: T = extractRequest[T](jsonRpcRequest)
    log.debug(s"Mcp request for [{}]: {}", jsonRpcRequest.method, mcpRequest)
    try {
      handler(mcpRequest)
        .map {
          case Some(mcpResult) =>
            Some(toJsonRpc(
              jsonRpcRequest.id.getOrElse(throw new IllegalArgumentException(
                s"Got a MCP result back [$mcpResult] for a request [$mcpRequest] with no id (notification), not allowed")),
              mcpResult))
          case None => None
        }(ExecutionContext.parasitic)
        .recover { case McpErrorException(error) =>
          Some(JsonRpc.JsonRpcErrorResponse(id = jsonRpcRequest.id, error = error))
        }(ExecutionContext.parasitic)
    } catch {
      case McpErrorException(error) =>
        Future.successful(Some(JsonRpc.JsonRpcErrorResponse(id = jsonRpcRequest.id, error = error)))
    }
  }

  private final case class McpErrorException(error: JsonRpc.JsonRpcError) extends RuntimeException

  sealed trait McpRequest {
    def _meta: Option[Meta]
  }

  sealed trait McpPaginatedRequest extends McpRequest {

    /**
     * @return
     *   An opaque token used to represent a cursor for pagination.
     */
    def cursor: Option[String]
  }

  sealed trait McpNotification {}

  sealed trait McpResult {

    /**
     * This result property is reserved by the protocol to allow clients and servers to attach additional _metadata to
     * their responses.
     */
    def _meta: Option[AnyRef]
  }

  sealed trait McpPaginatedResult extends McpResult {

    /**
     * @return
     *   An opaque token representing the pagination position after the last returned result. If present, there may be
     *   more results available.
     */
    def nextCursor: Option[String]
  }

  final case class Meta(progressToken: Option[Any])

  object InitializeRequest {
    def method: String = "initialize"
    def apply(initialize: InitializeRequest) = request(method, Some(initialize))
  }

  /**
   * Sent from the client to initialize a session
   *
   * @param protocolVersion
   *   The latest version of the Model Context Protocol that the client supports. The client MAY decide to support older
   *   versions as well.
   */
  final case class InitializeRequest(
      protocolVersion: String,
      capabilities: ClientCapabilities,
      clientInfo: Implementation,
      _meta: Option[Meta])
      extends McpRequest

  /**
   * Capabilities a client may support. Known capabilities are defined here, in this schema, but this is not a closed
   * set: any client can define its own, additional capabilities. FIXME how to model that
   *
   * @param experimental
   *   Experimental, non-standard capabilities that the client supports.
   * @param roots
   *   Present if the client supports listing roots
   * @param sampling
   *   Present if the client supports sampling from an LLM. (But what is that value then?
   */
  final case class ClientCapabilities(experimental: Map[String, AnyRef], roots: Option[Roots], sampling: AnyRef)
  final case class Roots(listChanged: Boolean)

  /**
   * Describes the name and version of an MCP implementation
   */
  final case class Implementation(name: String, version: String)

  /**
   * After receiving an initialized request from the client, the server sends this response
   */
  final case class InitializeResult(
      protocolVersion: String,
      capabilities: ServerCapabilities,
      serverInfo: Implementation,
      _meta: Option[Meta],
      instructions: String)
      extends McpResult

  /**
   * Capabilities that a server may support. Known capabilities are defined here, in this schema, but this is not a
   * closed set: any server can define its own, additional capabilities.
   *
   * @param experimental
   *   Experimental, non-standard capabilities that the server supports.
   * @param logging
   *   Present if the server supports sending log messages to the client.
   * @param completions
   *   Present if the server supports argument autocompletion suggestions.
   * @param prompts
   *   Present if the server offers any prompt templates.
   * @param resources
   *   Present if the server offers any resources to read
   * @param tools
   *   Present if the server offers any tools to call
   */
  final case class ServerCapabilities(
      experimental: Map[String, AnyRef],
      logging: Option[AnyRef],
      completions: Option[AnyRef],
      prompts: Option[Prompts],
      resources: Option[Resources],
      tools: Option[Tools])

  /**
   * @param listChanged
   *   Whether this server supports notifications for changes to the prompt list
   */
  final case class Prompts(listChanged: Boolean)

  /**
   * @param subscribe
   *   Whether this server supports subscribing to resource updates.
   * @param listChanged
   *   Whether this server supports notifications for changes to the resource list
   */
  final case class Resources(subscribe: Boolean, listChanged: Boolean)

  /**
   * @param listChanged
   *   Whether this server supports notifications for changes to the tool list
   */
  final case class Tools(listChanged: Boolean)

  /**
   * This notification is sent from the client to the server after initialization has finished.
   */
  object InitializedNotification {
    def method: String = "notifications/initialized"
    def apply(): JsonRpc.JsonRpcRequest = notification(method, None)
  }

  /**
   * A ping, issued by either the server or the client, to check that the other party is still alive. The receiver must
   * promptly respond, or else may be disconnected. FIXME respond with what?
   */
  object PingRequest {
    def method: String = "ping"
    def apply(): JsonRpc.JsonRpcRequest = request(method, None)
  }

  /**
   * An out-of-band notification used to inform the receiver of a progress update for a long-running request.
   */
  object ProgressNotification {
    def method: String = "notifications/progress"
    def apply(progress: ProgressNotification): JsonRpc.JsonRpcRequest = notification(method, Some(progress))
  }

  /**
   * An out-of-band notification used to inform the receiver of a progress update for a long-running request.
   *
   * @param progressToken
   *   The progress token which was given in the initial request, used to associate this notification with the request
   *   that is proceeding.
   * @param progress
   *   The progress thus far. This should increase every time progress is made, even if the total is unknown.
   * @param total
   *   Total number of items to process (or total progress required), if known.
   * @param message
   *   An optional message describing the current progress.
   */
  final case class ProgressNotification(
      progressToken: Either[String, Int],
      progress: Double,
      total: Option[Double],
      message: Option[String])
      extends McpNotification

  /**
   * Sent from the client to request a list of resources the server has.
   */
  object ListResourcesRequest {
    def method: String = "resources/list"
    def apply(listResources: ListResourcesRequest): JsonRpc.JsonRpcRequest = request(method, Some(listResources))
  }
  final case class ListResourcesRequest(cursor: Option[String], _meta: Option[Meta]) extends McpPaginatedRequest

  /**
   * The server's response to a resources/list request from the client.
   */
  final case class ListResourcesResult(
      @JsonInclude(JsonInclude.Include.ALWAYS)
      resources: Seq[Resource],
      _meta: Option[AnyRef],
      nextCursor: Option[String])
      extends McpPaginatedResult

  /**
   * A known resource that the server is capable of reading.
   * @param uri
   *   The URI of this resource.
   * @param name
   *   A human-readable name for this resource. This can be used by clients to populate UI elements.
   * @param description
   *   A description of what this resource represents. This can be used by clients to improve the LLM's understanding of
   *   available resources. It can be thought of like a "hint" to the model.
   * @param mimeType
   *   The MIME type of this resource, if known.
   * @param annotations
   *   Optional annotations for the client.
   * @param size
   *   The size of the raw resource content, in bytes (i.e., before base64 encoding or any tokenization), if known. This
   *   can be used by Hosts to display file sizes and estimate context window usage.
   */
  final case class Resource(
      uri: String,
      name: String,
      description: Option[String],
      mimeType: Option[String],
      annotations: Option[Annotations],
      size: Option[Long])

  /**
   * @param audience
   *   Describes who the intended customer of this object or data is. It can include multiple entries to indicate
   *   content useful for multiple audiences (e.g., `["user", "assistant"]`).
   * @param priority
   *   Describes how important this data is for operating the server. A value of 1 means "most important," and indicates
   *   that the data is effectively required, while 0 means "least important," and indicates that the data is entirely
   *   optional.
   */
  final case class Annotations(audience: Seq[Role], priority: Option[Double])

  /**
   * The sender or recipient of messages and data in a conversation. "user" or "assistant"
   */
  type Role = String

  /**
   * Sent from the client to request a list of resource templates the server has.
   */
  object ListResourcesTemplateRequest {
    def method: String = "resources/templates/list"
    def apply(listResourcesTemplateRequest: ListResourcesTemplateRequest) =
      request(method, Some(listResourcesTemplateRequest))
  }
  final case class ListResourcesTemplateRequest(_meta: Option[Meta], cursor: Option[String]) extends McpPaginatedRequest

  /**
   * The server's response to a resources/templates/list request from the client.
   */
  final case class ListResourceTemplateResult(
      @JsonInclude(JsonInclude.Include.ALWAYS)
      resourceTemplates: Seq[ResourceTemplate],
      nextCursor: Option[String],
      _meta: Option[Meta])
      extends McpPaginatedResult

  /**
   * A template description for resources available on the server.
   *
   * @param uriTemplate
   *   A URI template (according to RFC 6570) that can be used to construct resource URIs.
   * @param name
   *   A human-readable name for the type of resource this template refers to. This can be used by clients to populate
   *   UI elements.
   * @param description
   *   A description of what this template is for. This can be used by clients to improve the LLM's understanding of
   *   available resources. It can be thought of like a "hint" to the model.
   * @param mimeType
   *   The MIME type for all resources that match this template. This should only be included if all resources matching
   *   this template have the same type.
   * @param annotations
   *   Optional annotations for the client.
   */
  final case class ResourceTemplate(
      uriTemplate: String,
      name: String,
      description: Option[String],
      mimeType: Option[String],
      annotations: Option[Annotations])

  /**
   * Sent from the client to the server, to read a specific resource URI.
   */
  object ReadResourceRequest {
    def method = "resources/read"
    def apply(readResourceRequest: ReadResourceRequest) = request(method, Some(readResourceRequest))
  }
  final case class ReadResourceRequest(uri: String, _meta: Option[Meta]) extends McpRequest

  /**
   * The server's response to a resources/read request from the client.
   */
  final case class ReadResourceResult(contents: Seq[ResourceContents], _meta: Option[Meta]) extends McpResult
  sealed trait ResourceContents {
    def uri: String
    def mimeType: String
  }

  /**
   * @param text
   *   The text of the item. This must only be set if the item can actually be represented as text (not binary data).
   */
  final case class TextResourceContents(text: String, uri: String, mimeType: String) extends ResourceContents

  /**
   * @param blob
   *   A base64-encoded string representing the binary data of the item.
   */
  final case class BlobResourceContents(blob: String, uri: String, mimeType: String) extends ResourceContents

  /**
   * @return
   *   An optional notification from the server to the client, informing it that the list of resources it can read from
   *   has changed. This may be issued by servers without any previous subscription from the client.
   */
  def resourceListChangedNotification() = notification("notifications/resources/list_changed", None)

  object SubscribeRequest {
    def method = "resources/subscribe"
    def apply(subscribeRequest: SubscribeRequest) = request(method, Some(subscribeRequest))
  }

  /**
   * Sent from the client to request resources/updated notifications from the server whenever a particular resource
   * changes.
   */
  final case class SubscribeRequest(_meta: Option[Meta]) extends McpRequest

  object UnsubscribeRequest {
    def method = "resources/unsubscribe"
    def apply(unsubscribeRequest: UnsubscribeRequest) = request(method, Some(unsubscribeRequest))
  }

  /**
   * @param uri
   *   The URI of the resource to unsubscribe from
   */
  final case class UnsubscribeRequest(uri: String, _meta: Option[Meta]) extends McpRequest

  def resourceUpdatedNotification(update: ResourceUpdatedNotification) =
    notification("notifications/resources/updated", Some(update))
  case class ResourceUpdatedNotification(uri: String) extends McpNotification

  /**
   * Sent from the client to request a list of prompts and prompt templates the server has.
   */
  object ListPromptsRequest {
    def method = "prompts/list"
    def apply(prompsRequest: ListPromptsRequest) = request(method, None)
  }
  final case class ListPromptsRequest(_meta: Option[Meta], cursor: Option[String]) extends McpPaginatedRequest

  /**
   * The server's response to a prompts/list request from the client.
   */
  final case class ListPromptsResult(prompts: Seq[Prompt], nextCursor: Option[String], _meta: Option[Meta])
      extends McpPaginatedResult

  final case class Prompt(name: String, description: Option[String], arguments: Seq[PromptArgument])
  final case class PromptArgument(name: String, description: Option[String], required: Option[Boolean])

  object CancelledNotification {
    def method = "notifications/cancelled"
    def apply(cancelled: CancelledNotification): JsonRpcRequest = notification(method, Some(cancelled))
  }
  final case class CancelledNotification(requestId: Any, reason: String) extends McpNotification

  // server endpoint impl
  final case class McpDescriptor(
      resources: Seq[(Resource, () => Seq[ResourceContents])],
      resourceTemplates: Seq[ResourceTemplate])

  /**
   * Client connects to create a McpSession, responses are streamed from that, incoming requests for that session are
   * directed to it.
   */
  final class StatelessMcpEndpoint(descriptor: McpDescriptor)(implicit system: ActorSystem[_]) {
    private val log = LoggerFactory.getLogger(getClass)
    import system.executionContext

    private val resourcesByUri = descriptor.resources.map { case (resource, factory) => resource.uri -> factory }.toMap

    def httpEndpoint(): HttpEndpointDescriptor = {

      val methods = Map[String, JsonRpcRequest => Future[Option[JsonRpcResponse]]](
        InitializeRequest.method -> jsonRpcHandler[InitializeRequest](init),
        InitializedNotification.method -> notificationHandler,
        ListResourcesRequest.method -> jsonRpcHandler[ListResourcesRequest](listResources),
        ReadResourceRequest.method -> jsonRpcHandler[ReadResourceRequest](readResource),
        ListResourcesTemplateRequest.method -> jsonRpcHandler[ListResourcesTemplateRequest](listResourceTemplates),
        CancelledNotification.method -> notificationHandler)

      val jsonRpcEndpoint = new JsonRpc.JsonRpcEndpoint("/mcp", methods)
      jsonRpcEndpoint.httpEndpointDescriptor
    }

    private def init(initializeRequest: InitializeRequest): Future[Option[InitializeResult]] = {
      log.debug("MCP init request {}", initializeRequest)
      Future.successful(
        Some(
          InitializeResult(
            protocolVersion = ProtocolVersion,
            ServerCapabilities(
              experimental = Map.empty,
              logging = None,
              completions = None,
              prompts = None,
              resources =
                if (descriptor.resources.isEmpty && descriptor.resourceTemplates.isEmpty) None
                else Some(Resources(subscribe = false, listChanged = false)),
              tools = None),
            serverInfo = Implementation("Akka Service", "0.0.0"),
            instructions = "",
            _meta = None)))
    }

    private def notificationHandler(jsonRpcRequest: JsonRpcRequest): Future[Option[JsonRpcResponse]] = {
      require(jsonRpcRequest.isNotification, s"Handled [$jsonRpcRequest] as notification but it was a normal request")
      log.debug("MPC notitification {}", jsonRpcRequest)
      Future.successful(None)
    }

    private def listResources(listResourcesRequest: ListResourcesRequest): Future[Option[ListResourcesResult]] = {
      Future.successful(Some(ListResourcesResult(descriptor.resources.map(_._1), None, None)))
    }

    private def readResource(readResourceRequest: ReadResourceRequest): Future[Option[ReadResourceResult]] = {
      resourcesByUri.get(readResourceRequest.uri) match {
        case Some(factory) => Future.successful(Some(ReadResourceResult(factory(), None)))
        case None =>
          throw McpErrorException(
            JsonRpc.JsonRpcError(
              code = JsonRpc.JsonRpcError.Codes.McpResourceNotFound,
              message = "Resource not found",
              data = Some(Map("uri" -> readResourceRequest.uri))))
      }
    }

    private def listResourceTemplates(
        listResourcesTemplateRequest: ListResourcesTemplateRequest): Future[Option[ListResourceTemplateResult]] = {
      Future.successful(Some(ListResourceTemplateResult(descriptor.resourceTemplates, None, None)))
    }
  }

}
