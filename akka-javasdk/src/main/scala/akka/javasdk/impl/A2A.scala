/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import akka.runtime.sdk.spi.ACL
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.MethodOptions

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object A2A {

  /**
   * Describes the agent’s capabilities/skills and authentication mechanism
   * https://google.github.io/A2A/#/documentation?id=representation
   *
   * @param name
   *   Human-readable name of the agent
   * @param description
   *   A human-readable description of the agent. Used to assist users and other agents in understanding what the agent
   *   can do
   * @param url
   *   A URL to the address the agent is hosted at
   * @param provider
   *   The service provider of the agent
   * @param version
   *   The version of the agent - format is up to the provider. (e.g. "1.0.0")
   * @param documentationUrl
   *   A URL to documentation for the agent.
   * @param capabilities
   *   Optional capabilities supported by the agent.
   * @param authentication
   *   Authentication requirements for the agent. "Intended to match OpenAPI authentication structure"
   * @param defaultInputModes
   *   supported mime types for input, this can be overridden per-skill
   * @param defaultOutputModes
   *   supported mime types for output, this can be overridden per-skill
   * @param skills
   *   Skills are a unit of capability that an agent can perform.
   */
  final case class AgentCard(
      name: String,
      description: String,
      url: String,
      provider: Option[Provider],
      version: String,
      documentationUrl: Option[String],
      capabilities: Capabilities,
      authentication: Authentication,
      defaultInputModes: Seq[String],
      defaultOutputModes: Seq[String],
      // Note: spec says one rather than a list, but that must be wrong
      skills: Seq[Skill])
  final case class Provider(name: String, url: String)

  /**
   * @param streaming
   *   true if the agent supports SSE
   * @param pushNotifications
   *   true if the agent can notify updates to the client
   * @param stateTransitionHistory
   *   true if the agent exposes status change history for tasks
   */
  final case class Capabilities(streaming: Boolean, pushNotifications: Boolean, stateTransitionHistory: Boolean)

  /**
   * @param schemes
   *   e.g. Basic, Bearer
   * @param credentials
   *   credentials a client should use for private cards
   */
  final case class Authentication(schemes: Seq[String], credentials: Option[String])

  /**
   * @param id
   *   unique identifier for the agent's skill
   * @param name
   *   human-readable name of the skill
   * @param description
   *   description of the skill - will be used by the client or a human as a hint to understand what the skill does
   * @param tags
   *   Set of tagwords describing classes of capabilities for this specific skill (e.g. "cooking", "customer support",
   *   "billing")
   * @param examples
   *   example prompts for tasks. Will be used by the client as a hint to understand how the skill can be used. (e.g. "I
   *   need a recipe for bread")
   * @param inputModes
   *   supported mime types for input, if different from default
   * @param outputModes
   *   supported mime types for output, if different from default
   */
  final case class Skill(
      id: String,
      name: String,
      description: String,
      tags: Seq[String],
      examples: Seq[String],
      inputModes: Seq[String] = Seq.empty,
      outputModes: Seq[String] = Seq.empty)

  /**
   * Represents a message in the A2A protocol.
   *
   * @param role
   *   "user" or "agent"
   */
  final case class Message(role: String, parts: Seq[Part], metadata: Map[String, Any])

  /**
   * Agents generate Artifacts as an end result of a Task. Artifacts are immutable, can be named, and can have multiple
   * parts. A streaming response can append parts to existing Artifacts
   */
  final case class Artifact(
      name: Option[String],
      description: Option[String],
      parts: Seq[Part],
      metadata: Map[String, Any],
      index: Number,
      append: Option[Boolean] = None,
      lastChunk: Option[Boolean] = None)

  sealed trait Part {
    def metadata: Map[String, Any]
  }
  final case class TextPart(text: String, metadata: Map[String, Any] = Map.empty, `type`: String = "text") extends Part
  final case class FilePart(file: FileDetails, metadata: Map[String, Any] = Map.empty, `type`: String = "file")
      extends Part
  final case class DataPart(data: Map[String, Any], metadata: Map[String, Any] = Map.empty, `type`: String = "data")
      extends Part

  /**
   * @param bytes
   *   base64 encoded bytes, if not present uri must be present, and not both
   * @param uri
   *   uri to the file contents, if not present bytes must be present, and not both
   */
  final case class FileDetails(
      name: Option[String],
      mimeType: Option[String],
      bytes: Option[String],
      uri: Option[String])

  /**
   * Represents the state of a task.
   */
  sealed trait TaskState
  object TaskState {
    case object Submitted extends TaskState
    case object Working extends TaskState
    case object InputRequired extends TaskState
    case object Completed extends TaskState
    case object Canceled extends TaskState
    case object Failed extends TaskState
    case object Unknown extends TaskState
  }

  /**
   * Configuration for push notifications.
   */
  final case class PushNotificationConfig(url: String)

  /**
   * Represents a task in the A2A protocol.
   *
   * @param id
   *   unique identifier for the task
   * @param sessionId
   *   client-generated id for the session holding the task
   * @param status
   *   current status of the task
   * @param history
   *   optional history of messages
   * @param artifacts
   *   optional collection of artifacts created by the agent
   * @param metadata
   *   optional extension metadata
   */
  final case class Task(
      id: String,
      sessionId: String,
      status: TaskStatus,
      history: Option[Seq[Message]] = None,
      artifacts: Option[Seq[Artifact]] = None,
      metadata: Map[String, Any] = Map.empty)

  /**
   * Represents the status of a task.
   *
   * @param state
   *   the state of the task
   * @param message
   *   optional additional status updates for the client
   * @param timestamp
   *   optional ISO datetime value
   */
  final case class TaskStatus(state: TaskState, message: Option[Message] = None, timestamp: Option[String] = None)

  /**
   * Event sent by server during sendSubscribe or subscribe requests.
   *
   * @param id
   *   task id for the task that the update is for
   * @param final
   *   indicates the end of the event stream
   */
  final case class TaskStatusUpdateEvent(
      id: String,
      status: TaskStatus,
      `final`: Boolean,
      metadata: Map[String, Any] = Map.empty)

  /**
   * Event sent by server during sendSubscribe or subscribe requests.
   *
   * @param id
   *   task id that the update is for
   */
  final case class TaskArtifactUpdateEvent(id: String, artifact: Artifact, metadata: Option[Map[String, Any]] = None)

  /**
   * Parameters sent by the client to the agent to create, continue, or restart a task.
   *
   * @param id
   *   task id
   * @param sessionId
   *   server creates a new sessionId for new tasks if not set
   * @param historyLength
   *   optional number of recent messages to be retrieved
   * @param pushNotification
   *   optional where the server should send notifications when disconnected
   */
  final case class TaskSendParams(
      id: String,
      sessionId: Option[String],
      message: Message,
      historyLength: Option[Int] = None,
      pushNotification: Option[PushNotificationConfig] = None,
      metadata: Map[String, Any] = Map.empty)

  object ErrorCodes {

    /** No task found with the provided id */
    val TaskNotFound = -32000

    /** Task cannot be canceled by the remote agent */
    val TaskCannotBeCanceled = -32002

    /**
     * Push Notification is not supported by the agent
     */
    val PushNotificationsNotSupported = -32003

    /**
     * Operation is not supported
     */
    val UnsupportedOperation = -32004

    /**
     * Incompatible content types between client and an agent
     */
    val IncompatibleContentTypes = -32005
  }

  def dummyA2aEndpoint()(implicit executionContext: ExecutionContext): Seq[HttpEndpointDescriptor] = {
    new A2AEndpoint(
      AgentCard(
        "example service",
        "a service that can do example stuff",
        "https://example.com",
        Some(Provider("example", "https://example.com")),
        "1.0.0",
        Some("https://example.com/docs"),
        Capabilities(streaming = false, pushNotifications = false, stateTransitionHistory = false),
        Authentication(Seq(), None),
        Seq("text/plain"),
        Seq("text/plain"),
        Seq(Skill(
          "skill-one",
          "Example skill",
          "An example skill that does not really do anything useful",
          Seq("example"),
          Seq("Please run an example that doesn't really do anything useful"))))).httpEndpoint()
  }

  final class A2AEndpoint(card: AgentCard)(implicit ec: ExecutionContext) {
    def httpEndpoint(): Seq[HttpEndpointDescriptor] = {
      def notImplementedYet: JsonRpc.JsonRpcRequest => Future[Option[JsonRpc.JsonRpcResponse]] = r =>
        Future.successful(
          Some(
            JsonRpc.JsonRpcErrorResponse(
              id = r.id,
              error = JsonRpc.JsonRpcError(JsonRpc.JsonRpcError.Codes.MethodNotFound, "Not implemented yet"))))

      val methods =
        Map[String, JsonRpc.JsonRpcRequest => Future[Option[JsonRpc.JsonRpcResponse]]](
          "tasks/send" -> notImplementedYet,
          "tasks/get" -> notImplementedYet,
          "tasks/cancel" -> notImplementedYet,
          "tasks/pushNotification/set" -> notImplementedYet,
          "tasks/pushNotification/get" -> notImplementedYet)

      val jsonRpcEndpointDescriptor = new JsonRpc.JsonRpcEndpoint("/a2a", methods).httpEndpointDescriptor
      val agentCardEndpoint = new HttpEndpointDescriptor(
        mainPath = None,
        instanceFactory = _ => this,
        methods = Seq(
          new HttpEndpointMethodDescriptor(
            HttpMethods.GET,
            "/.well-known/agent.json",
            classOf[A2AEndpoint].getMethod("agentCard"),
            new MethodOptions(None, None))),
        componentOptions =
          new ComponentOptions(Some(new ACL(Seq(All), Seq.empty, None, None)), None), // TODO more limiting ACL?,
        implementationClassName = classOf[A2AEndpoint].getName,
        objectMapper = None)

      Seq(jsonRpcEndpointDescriptor, agentCardEndpoint)
    }

    // http endpoint method for '/.well-known/agent.json'
    def agentCard(): AgentCard = card
  }

}
