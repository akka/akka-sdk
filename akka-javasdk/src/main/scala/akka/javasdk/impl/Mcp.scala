package akka.javasdk.impl

import akka.annotation.InternalApi

import java.util.concurrent.atomic.AtomicLong

@InternalApi
private[akka] object Mcp {

  private val requestId = new AtomicLong(0)

  private def notification(method: String, params: Option[McpNotification]) =
    JsonRpc.JsonRpcRequest(method = method, params = params, id = None)

  private def request(method: String, mcpRequest: Option[McpRequest] = None): JsonRpc.JsonRpcRequest =
    JsonRpc.JsonRpcRequest(method = method, params = mcpRequest, id = Some(requestId.incrementAndGet()))

  sealed trait McpRequest {
    def meta: Option[Meta]
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
     * This result property is reserved by the protocol to allow clients and servers to attach additional metadata to
     * their responses.
     */
    def meta: Option[AnyRef]
  }

  sealed trait McpPaginatedResult extends McpResult {

    /**
     * @return
     *   An opaque token representing the pagination position after the last returned result. If present, there may be
     *   more results available.
     */
    def nextCursor: Option[String]
  }

  final case class Meta(progressToken: Option[String])

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
      meta: Option[Meta])
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
  final case class InitializeResult(capabilities: ServerCapabilities, serverInfo: Implementation)

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
    def apply(): JsonRpc.JsonRpcRequest = request(method, None)
  }

  /**
   * The server's response to a resources/list request from the client.
   */
  final case class ListResourcesResult(resources: Seq[Resource], meta: Option[AnyRef], nextCursor: Option[String])
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
  final case class ListResourcesTemplateRequest(meta: Option[Meta], cursor: Option[String]) extends McpPaginatedRequest

  /**
   * The server's response to a resources/templates/list request from the client.
   */
  final case class ListResourceTemplateResult(
      resourceTemplates: Seq[ResourceTemplate],
      nextCursor: Option[String],
      meta: Option[Meta])
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
  final case class ReadResourceRequest(uri: String, meta: Option[Meta]) extends McpRequest

  /**
   * The server's response to a resources/read request from the client.
   */
  final case class ReadResourceResult(content: ResourceContents, meta: Option[Meta]) extends McpResult
  sealed trait ResourceContents

  /**
   * @param text
   *   The text of the item. This must only be set if the item can actually be represented as text (not binary data).
   */
  final case class TextResourceContents(text: String) extends ResourceContents

  /**
   * @param blob
   *   A base64-encoded string representing the binary data of the item.
   */
  final case class BlobResourceContents(blob: String) extends ResourceContents

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
  final case class SubscribeRequest(meta: Option[Meta]) extends McpRequest

  object UnsubscribeRequest {
    def method = "resources/unsubscribe"
    def apply(unsubscribeRequest: UnsubscribeRequest) = request(method, Some(unsubscribeRequest))
  }

  /**
   * @param uri
   *   The URI of the resource to unsubscribe from
   */
  final case class UnsubscribeRequest(uri: String, meta: Option[Meta]) extends McpRequest

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
  final case class ListPromptsRequest(meta: Option[Meta], cursor: Option[String]) extends McpPaginatedRequest

  /**
   * The server's response to a prompts/list request from the client.
   */
  final case class ListPromptsResult(prompts: Seq[Prompt], nextCursor: Option[String], meta: Option[Meta])
      extends McpPaginatedResult

  final case class Prompt(name: String, description: Option[String], arguments: Seq[PromptArgument])
  final case class PromptArgument(name: String, description: Option[String], required: Option[Boolean])

}
