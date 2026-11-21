/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.annotation.InternalApi
import akka.javasdk.SanitizerClient
import akka.javasdk.a2a.A2aArtifact
import akka.javasdk.a2a.A2aMessage
import akka.javasdk.a2a.A2aPart
import akka.javasdk.a2a.A2aSendMessageResponse
import akka.javasdk.a2a.A2aStreamResponse
import akka.javasdk.a2a.A2aTask
import akka.javasdk.a2a.A2aTaskArtifactUpdate
import akka.javasdk.a2a.A2aTaskStatus
import akka.javasdk.a2a.A2aTaskStatusUpdate
import akka.javasdk.a2a.A2aTaskUpdate
import akka.javasdk.agent.ToolCallGuardrail
import akka.javasdk.impl.agent.GuardrailProvider
import akka.javasdk.impl.agent.GuardrailProvider.AgentGuardrails
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API
 *
 * The guardrails and sanitizers of calls to remote A2A agents that application code makes with `A2aClient`, such as a
 * workflow step. A call from an agent's tool is governed by the runtime's agent loop instead, so the client of the tool
 * path has no governance.
 *
 * @param guardrailsFor
 *   the guardrails of a component, by component id and agent role
 * @param toolResultSanitizersFor
 *   the names of the sanitizers that mask tool results for a component, by component id and agent role
 * @param roleOf
 *   the agent role of a component, when it is an agent with a role
 */
@InternalApi
private[akka] final class A2aGovernance(
    guardrailsFor: (String, Option[String]) => AgentGuardrails,
    toolResultSanitizersFor: (String, Option[String]) => Seq[String],
    sanitizerClient: SanitizerClient,
    roleOf: String => Option[String],
    tracerFactory: () => Tracer)(implicit ec: ExecutionContext) {

  /**
   * The governance of the calls of one component.
   *
   * @param callerComponentId
   *   the id of the calling component, or none for code outside a component
   */
  def forCaller(callerComponentId: Option[String], telemetryContext: Option[OtelContext]): A2aCallGovernance = {
    val componentId = callerComponentId.getOrElse("")
    val role = callerComponentId.flatMap(roleOf)
    new A2aCallGovernance(
      componentId,
      guardrailsFor(componentId, role),
      toolResultSanitizersFor(componentId, role),
      sanitizerClient,
      telemetryContext,
      tracerFactory)
  }

  /** The tool-result sanitizers of a component, for a reply that reaches it outside a call it made. */
  def sanitizersOf(componentId: String): A2aReplySanitizer =
    new A2aReplySanitizer(toolResultSanitizersFor(componentId, roleOf(componentId)), sanitizerClient)
}

/**
 * INTERNAL API
 *
 * Masks the text of replies from remote agents with sanitizers, in the order given: the text parts, and the strings in
 * the data parts, of messages, task statuses, artifacts and history.
 */
@InternalApi
private[akka] final class A2aReplySanitizer(sanitizerNames: Seq[String], client: SanitizerClient)(implicit
    ec: ExecutionContext) {

  def isEmpty: Boolean = sanitizerNames.isEmpty

  def text(value: String): Future[String] =
    if (value == null || value.isEmpty) Future.successful(value)
    else
      sanitizerNames.foldLeft(Future.successful(value)) { (masked, name) =>
        masked.flatMap(t => client.sanitizeAsync(name, t).asScala)
      }

  def response(response: A2aSendMessageResponse): Future[A2aSendMessageResponse] =
    if (isEmpty) Future.successful(response)
    else
      response match {
        case m: A2aSendMessageResponse.Message => message(m.message()).map(A2aSendMessageResponse.of)
        case t: A2aSendMessageResponse.Task    => task(t.task()).map(A2aSendMessageResponse.of)
      }

  def event(event: A2aStreamResponse): Future[A2aStreamResponse] =
    if (isEmpty) Future.successful(event)
    else
      event match {
        case t: A2aStreamResponse.Task    => task(t.task()).map(A2aStreamResponse.of)
        case m: A2aStreamResponse.Message => message(m.message()).map(A2aStreamResponse.of)
        case s: A2aStreamResponse.StatusUpdate =>
          update(s.statusUpdate()).map(A2aStreamResponse.of)
        case a: A2aStreamResponse.ArtifactUpdate =>
          update(a.artifactUpdate()).map(A2aStreamResponse.of)
      }

  def update(update: A2aTaskUpdate): Future[A2aTaskUpdate] =
    if (isEmpty) Future.successful(update)
    else
      update match {
        case s: A2aTaskStatusUpdate =>
          status(s.status()).map(new A2aTaskStatusUpdate(s.taskId(), s.contextId(), _, s.metadata()))
        case a: A2aTaskArtifactUpdate =>
          artifact(a.artifact())
            .map(new A2aTaskArtifactUpdate(a.taskId(), a.contextId(), _, a.append(), a.lastChunk(), a.metadata()))
      }

  def task(task: A2aTask): Future[A2aTask] =
    if (isEmpty || task == null) Future.successful(task)
    else
      for {
        st <- status(task.status())
        artifacts <- Future.sequence(task.artifacts().asScala.toSeq.map(artifact))
        history <- Future.sequence(task.history().asScala.toSeq.map(message))
      } yield new A2aTask(task.id(), task.contextId(), st, artifacts.asJava, history.asJava, task.metadata())

  private def status(status: A2aTaskStatus): Future[A2aTaskStatus] =
    if (status == null || status.message() == null) Future.successful(status)
    else message(status.message()).map(new A2aTaskStatus(status.state(), _, status.timestamp()))

  def message(message: A2aMessage): Future[A2aMessage] =
    if (message == null) Future.successful(message)
    else parts(message.parts()).map(message.withParts)

  private def artifact(artifact: A2aArtifact): Future[A2aArtifact] =
    if (artifact == null) Future.successful(artifact)
    else
      parts(artifact.parts()).map(
        new A2aArtifact(
          artifact.artifactId(),
          artifact.name(),
          artifact.description(),
          _,
          artifact.metadata(),
          artifact.extensions()))

  private def parts(parts: java.util.List[A2aPart]): Future[java.util.List[A2aPart]] =
    Future.sequence(parts.asScala.toSeq.map(part)).map(_.asJava)

  private def part(part: A2aPart): Future[A2aPart] = part match {
    case t: A2aPart.Text =>
      text(t.text()).map(masked => new A2aPart.Text(masked, t.mediaType(), t.filename(), t.metadata()))
    case d: A2aPart.Data =>
      data(d.data()).map(masked => new A2aPart.Data(masked, d.mediaType(), d.filename(), d.metadata()))
    case other => Future.successful(other)
  }

  // The strings of a JSON value, at any depth. Keys are left as they are.
  private def data(value: Any): Future[AnyRef] = value match {
    case s: String => text(s)
    case m: java.util.Map[_, _] =>
      Future
        .sequence(m.asScala.toSeq.map { case (k, v) => data(v).map(k -> _) })
        .map { entries =>
          val result = new java.util.LinkedHashMap[Any, AnyRef]()
          entries.foreach { case (k, v) => result.put(k, v) }
          result
        }
    case l: java.util.List[_] => Future.sequence(l.asScala.toSeq.map(data)).map(_.asJava)
    case other                => Future.successful(other.asInstanceOf[AnyRef])
  }
}

/**
 * INTERNAL API
 *
 * The guardrails and sanitizers of the calls of one component.
 */
@InternalApi
private[akka] final class A2aCallGovernance(
    val callerComponentId: String,
    guardrails: AgentGuardrails,
    sanitizerNames: Seq[String],
    sanitizerClient: SanitizerClient,
    telemetryContext: Option[OtelContext],
    tracerFactory: () => Tracer)(implicit ec: ExecutionContext) {

  val replies: A2aReplySanitizer = new A2aReplySanitizer(sanitizerNames, sanitizerClient)

  /**
   * Runs the ToolCallGuardrails that select the call before it is sent. The guardrails see the origin `RemoteA2a`, the
   * calling component as the agent id, the context id as the session id, the tool name that an agent's tool for the
   * skill has, and the message as its JSON.
   *
   * @param skill
   *   the skill the message names, or none when the remote agent chooses
   */
  def beforeSend(
      agentName: String,
      cardUrl: String,
      skill: Option[String],
      message: A2aMessage,
      contextId: Option[String]): Future[Unit] = {
    val origin = new ToolCallGuardrail.ToolOrigin.RemoteA2a(agentName, cardUrl, skill.getOrElse(""))
    val toolName = A2aAgentTools.toolName(agentName, skill)
    val entries = guardrails.toolCallGuardrailEntries(toolName, origin)
    if (entries.isEmpty) Future.unit
    else {
      val ctx = new GuardrailProvider.ToolCallGuardrailCallContextImpl(
        callerComponentId,
        toolName,
        "",
        RemoteA2aAgentState.mapper.writeValueAsString(message),
        contextId.getOrElse(""),
        origin,
        telemetryContext,
        tracerFactory)
      GuardrailProvider.evaluateToolCall(entries, ctx)
    }
  }
}
