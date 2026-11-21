/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.a2a.A2aAgentCard
import akka.javasdk.a2a.A2aAgentSkill
import akka.javasdk.a2a.A2aClientException
import akka.javasdk.a2a.A2aMessage
import akka.javasdk.a2a.A2aPart
import akka.javasdk.a2a.A2aSendMessageResponse
import akka.javasdk.a2a.A2aTask
import akka.javasdk.a2a.A2aTaskState
import akka.javasdk.agent.RemoteA2aAgent
import akka.javasdk.agent.ToolCallGuardrail
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiJsonSchema
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 *
 * The skills of remote A2A agents offered to a model as tools.
 */
@InternalApi
private[akka] object A2aAgentTools {

  private val log = LoggerFactory.getLogger(getClass)
  private val mapper = JsonSupport.getObjectMapper

  // The rules that every supported model provider accepts: letters, digits, '_' and '-', at most 64 characters, and
  // a letter or '_' first.
  private val MaxToolNameLength = 64

  /**
   * @param agentName
   *   the configured name of the remote agent, or the name on its card
   */
  final case class A2aTool(
      descriptor: SpiAgent.ToolDescriptor,
      agent: RemoteA2aAgentImpl,
      agentName: String,
      cardUrl: String,
      skillId: String) {
    def name: String = descriptor.name

    def origin: ToolCallGuardrail.ToolOrigin =
      new ToolCallGuardrail.ToolOrigin.RemoteA2a(agentName, cardUrl, skillId)
  }

  private val inputSchema =
    new SpiJsonSchema.JsonSchemaObject(
      description = None,
      properties = Map(
        "message" -> new SpiJsonSchema.JsonSchemaString(Some("The message to send to the remote agent")),
        "data" -> new SpiJsonSchema.JsonSchemaObject(
          Some("Optional structured data to send with the message, as a JSON object"),
          properties = Map.empty,
          required = Seq.empty)),
      required = Seq("message"))

  /**
   * One tool for each allowed skill of each remote agent. Reads the Agent Cards, which the client caches, and blocks
   * while a card is fetched. A remote agent whose card cannot be read offers no tools for this request, and a warning
   * is logged, so that one unavailable agent does not fail the agent request.
   */
  def toolsFor(agents: Seq[RemoteA2aAgent], client: A2aClientImpl): Seq[A2aTool] =
    agents.flatMap {
      case agent: RemoteA2aAgentImpl =>
        readCard(agent, client) match {
          case Some(card) => toolsFor(agent, card, client)
          case None       => Seq.empty
        }
      case other =>
        throw new IllegalArgumentException(s"Unsupported RemoteA2aAgent implementation [${other.getClass.getName}]")
    }

  private def readCard(agent: RemoteA2aAgentImpl, client: A2aClientImpl): Option[A2aAgentCard] =
    try Some(client.forTarget(agent.target, agent.timeout).card().invoke())
    catch {
      // Settings that are not valid are a mistake in the service, not an unavailable agent
      case e: IllegalArgumentException => throw e
      case NonFatal(e) =>
        log.warn(
          "The Agent Card of remote A2A agent [{}] could not be read, so its skills are not offered as tools: {}",
          agent.target.name,
          e.getMessage)
        None
    }

  private def toolsFor(agent: RemoteA2aAgentImpl, card: A2aAgentCard, client: A2aClientImpl): Seq[A2aTool] = {
    val cardUrl = client.remoteAgents.stateFor(agent.target).settings.cardUrl
    val agentName = client.remoteAgents.agentName(agent.target, cardUrl, card)
    val skills = Option(card.skills()).map(_.asScala.toSeq).getOrElse(Seq.empty).filter { skill =>
      skill.id() != null && !skill.id().isBlank && agent.allowedSkills.forall(_.contains(skill.id()))
    }
    agent.allowedSkills.foreach { allowed =>
      val missing = allowed -- skills.map(_.id())
      if (missing.nonEmpty)
        log.warn(
          "The Agent Card of remote A2A agent [{}] has no skills [{}]",
          agentName,
          missing.toSeq.sorted.mkString(", "))
    }
    skills.map(skill =>
      A2aTool(descriptor(toolName(agentName, skill.id()), card, skill), agent, agentName, cardUrl, skill.id()))
  }

  /** Rejects tools that share a name with each other or with the agent's other tools. */
  def validateNames(tools: Seq[A2aTool], otherToolNames: Seq[String]): Unit = {
    val duplicates = (tools.map(_.name) ++ otherToolNames).groupBy(identity).collect {
      case (name, occurrences) if occurrences.size > 1 && tools.exists(_.name == name) => name
    }
    if (duplicates.nonEmpty)
      throw new IllegalArgumentException(
        s"The tool names [${duplicates.toSeq.sorted.mkString(", ")}] of remote A2A agents clash with other tools " +
        "of the agent. Rename a function tool, or limit the skills with RemoteA2aAgent.withAllowedSkills.")
  }

  /**
   * The tool name for a skill: `<agent>_<skill>`, with every character the model providers refuse replaced by `_`. A
   * name longer than the limit is cut, and ends with a hash of the full name so that it stays distinct.
   */
  def toolName(agentName: String, skillId: String): String = toolName(agentName, Some(skillId))

  /** The tool name for a skill, or for the agent alone when no skill is named: `<agent>`. */
  def toolName(agentName: String, skillId: Option[String]): String = {
    val full = skillId.fold(agentName)(skill => s"${agentName}_$skill")
    val cleaned = full.map(c => if (isNameChar(c)) c else '_')
    val prefixed = if (cleaned.head.isLetter || cleaned.head == '_') cleaned else s"a2a_$cleaned"
    if (prefixed.length <= MaxToolNameLength) prefixed
    else {
      val hash = f"${full.hashCode}%08x"
      prefixed.take(MaxToolNameLength - hash.length - 1) + "_" + hash
    }
  }

  private def isNameChar(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-'

  private def descriptor(name: String, card: A2aAgentCard, skill: A2aAgentSkill): SpiAgent.ToolDescriptor = {
    val skillName = Option(skill.name()).filterNot(_.isBlank).getOrElse(skill.id())
    val about = Option(skill.description()).filterNot(_.isBlank).getOrElse(skillName)
    val agentName = Option(card.name()).filterNot(_.isBlank).getOrElse("remote agent")
    new SpiAgent.ToolDescriptor(name, s"$about (skill [$skillName] of the remote agent [$agentName])", inputSchema, Nil)
  }

  /**
   * Sends the model's tool call to the skill and returns the reply as text. A failure of the call, or tool arguments
   * that cannot be sent, are returned as text that starts with `Error:`, so that the model sees them.
   *
   * @param contextId
   *   the session id of the agent, sent as the A2A context id
   * @param agentComponentId
   *   the component id of the calling agent, whose session memory receives the outcome of a handed off task
   * @param writesSessionMemory
   *   true when the agent writes the built-in session memory, which is the only place the outcome of a handed off task
   *   can go; otherwise a task still working after the hand-off time is not followed
   */
  def call(
      tool: A2aTool,
      arguments: String,
      contextId: String,
      agentComponentId: String,
      writesSessionMemory: Boolean,
      client: A2aClientImpl)(implicit ec: ExecutionContext, scheduler: akka.actor.Scheduler): Future[String] = {
    val message =
      try Right(toMessage(arguments))
      catch { case NonFatal(e) => Left(s"Error: the tool call could not be sent: ${e.getMessage}") }
    message match {
      case Left(error) => Future.successful(error)
      case Right(msg) =>
        val sent =
          try {
            val remote = client.forTarget(tool.agent.target, tool.agent.timeout)
            tool.agent.handOffAfter match {
              case None =>
                remote.send(tool.skillId, msg).inContext(contextId).invokeAsync().asScala.map(replyText)
              case Some(handOffAfter) =>
                remote
                  .sendFuture(Some(tool.skillId), msg, Some(contextId), returnImmediately = true)
                  .flatMap {
                    case t: A2aSendMessageResponse.Task =>
                      val deadline = System.nanoTime() + handOffAfter.toNanos
                      followUntil(remote, t.task(), deadline).flatMap { task =>
                        if (A2aRemoteTasks.settles(task)) Future.successful(replyText(A2aSendMessageResponse.of(task)))
                        else if (!remote.canFollowTasks) {
                          warnNoRemoteTaskComponents(tool.name)
                          Future.successful(replyText(A2aSendMessageResponse.of(task)))
                        } else if (!writesSessionMemory) {
                          log.info(
                            "Remote A2A task [{}] of tool [{}] is still working after the hand-off time, and agent [{}] " +
                            "does not write the built-in session memory, so the task is not followed",
                            task.id(),
                            tool.name,
                            agentComponentId)
                          Future.successful(notFollowedText(task, handOffAfter))
                        } else
                          remote
                            .handOff(task, contextId, agentComponentId, tool.name)
                            .map(_ => handedOffText(task, handOffAfter))
                      }
                    case other => Future.successful(replyText(other))
                  }
            }
          } catch { case NonFatal(e) => Future.failed(e) }
        sent.recover { case NonFatal(e) =>
          log.debug("The call of tool [{}] to remote A2A agent [{}] failed", tool.name, tool.agentName, e)
          e match {
            case clientError: A2aClientException => s"Error: ${clientError.getMessage}"
            case other => s"Error: the call to remote agent [${tool.agentName}] failed: ${other.getMessage}"
          }
        }
    }
  }

  private val warnedNoRemoteTaskComponents = new java.util.concurrent.atomic.AtomicBoolean(false)

  private def warnNoRemoteTaskComponents(toolName: String): Unit =
    if (warnedNoRemoteTaskComponents.compareAndSet(false, true))
      log.warn(
        "Tool [{}] asks to hand off remote A2A tasks, but the service has no components that follow remote tasks, " +
        "so the tasks are not handed off. Set akka.javasdk.a2a.remote-tasks.enabled = on, or configure the agent " +
        "under akka.javasdk.a2a.remote-agents.",
        toolName)

  // How often a task is read while the tool waits for it, before it is handed off
  private val FollowInterval = 500.millis

  // Reads the task until it ends or needs input, or until the deadline passes
  private def followUntil(remote: RemoteA2aAgentClientImpl, task: A2aTask, deadlineNanos: Long)(implicit
      ec: ExecutionContext,
      scheduler: akka.actor.Scheduler): Future[A2aTask] = {
    val remaining = deadlineNanos - System.nanoTime()
    if (A2aRemoteTasks.settles(task) || remaining <= 0) Future.successful(task)
    else {
      val delay = FollowInterval.min(remaining.nanos)
      akka.pattern
        .after(delay, scheduler)(remote.getTaskAsync(task.id()))
        .flatMap(followUntil(remote, _, deadlineNanos))
    }
  }

  private def handedOffText(task: A2aTask, handOffAfter: FiniteDuration): String =
    s"The remote task [${task.id()}] was still working after ${handOffAfter.toCoarsest}, so it continues in the " +
    "background. Its outcome will be added to this conversation when it is known."

  private def notFollowedText(task: A2aTask, handOffAfter: FiniteDuration): String =
    s"The remote task [${task.id()}] was still working after ${handOffAfter.toCoarsest}, so it continues in the " +
    "background. Its outcome will not be added to this conversation."

  private def toMessage(arguments: String): A2aMessage = {
    val json = mapper.readTree(arguments)
    val text = Option(json.get("message")).filter(_.isTextual).map(_.asText()).filterNot(_.isBlank)
    val dataPart = Option(json.get("data")).filterNot(_.isNull).map { data =>
      if (!data.isObject) throw new IllegalArgumentException("the data argument must be a JSON object")
      A2aPart.data(mapper.convertValue(data, classOf[java.util.Map[String, Object]]))
    }
    val textPart =
      A2aPart.text(text.getOrElse(throw new IllegalArgumentException("the message argument is missing or empty")))
    A2aMessage.user(textPart, dataPart.toSeq: _*)
  }

  def replyText(response: A2aSendMessageResponse): String =
    response match {
      case m: A2aSendMessageResponse.Message => partsText(m.message().parts().asScala.toSeq)
      case t: A2aSendMessageResponse.Task    => taskText(t.task())
    }

  private def taskText(task: A2aTask): String = {
    val state = Option(task.status()).flatMap(s => Option(s.state())).getOrElse(A2aTaskState.UNSPECIFIED)
    val statusText =
      Option(task.status()).flatMap(s => Option(s.message())).map(m => partsText(m.parts().asScala.toSeq))
    state match {
      case A2aTaskState.COMPLETED =>
        val content = task.artifacts().asScala.toSeq.map(a => partsText(a.parts().asScala.toSeq)) ++ statusText
        val nonEmpty = content.filterNot(_.isBlank)
        if (nonEmpty.isEmpty) "The remote agent completed the task without content."
        else nonEmpty.mkString("\n")
      case A2aTaskState.FAILED | A2aTaskState.REJECTED | A2aTaskState.CANCELED =>
        s"Error: the remote task [${task.id()}] ended in state ${state.wireName()}" + statusText.fold("")(t => s": $t")
      case _ =>
        s"The remote task [${task.id()}] is in state ${state.wireName()}" + statusText.fold("")(t => s": $t")
    }
  }

  private def partsText(parts: Seq[A2aPart]): String =
    parts
      .map {
        case text: A2aPart.Text => text.text()
        case data: A2aPart.Data => mapper.writeValueAsString(data.data())
        case url: A2aPart.Url   => s"[file ${fileLabel(url)}: ${url.url()}]"
        case raw: A2aPart.Raw   => s"[file ${fileLabel(raw)}, content not shown]"
      }
      .mkString("\n")

  private def fileLabel(part: A2aPart): String =
    Seq(Option(part.filename()), Option(part.mediaType()).map(t => s"($t)")).flatten.mkString(" ") match {
      case ""    => "without a name"
      case label => label
    }
}
