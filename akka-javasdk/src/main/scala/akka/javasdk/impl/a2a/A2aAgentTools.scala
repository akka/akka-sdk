/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
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
import akka.javasdk.a2a.A2aRole
import akka.javasdk.a2a.A2aSendMessageResponse
import akka.javasdk.a2a.A2aTask
import akka.javasdk.a2a.A2aTaskState
import akka.javasdk.a2a.RemoteA2aAgent
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

  final case class A2aTool(descriptor: SpiAgent.ToolDescriptor, agent: RemoteA2aAgentImpl, skillId: String) {
    def name: String = descriptor.name

    def origin(caller: String): ToolCallGuardrail.ToolOrigin =
      new ToolCallGuardrail.ToolOrigin.RemoteA2a(agent.nameOrCardUrl, skillId, caller)
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
   * while a card is fetched.
   */
  def toolsFor(agents: Seq[RemoteA2aAgent], client: A2aClientImpl): Seq[A2aTool] =
    agents.flatMap {
      case agent: RemoteA2aAgentImpl =>
        val card = client.forAgent(agent.nameOrCardUrl, agent.timeout).card()
        val agentName =
          if (agent.isCardUrl) Option(card.name()).filterNot(_.isBlank).getOrElse("remote_agent")
          else agent.nameOrCardUrl
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
        skills.map(skill => A2aTool(descriptor(toolName(agentName, skill.id()), card, skill), agent, skill.id()))
      case other =>
        throw new IllegalArgumentException(s"Unsupported RemoteA2aAgent implementation [${other.getClass.getName}]")
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
  def toolName(agentName: String, skillId: String): String = {
    val full = s"${agentName}_$skillId"
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
   */
  def call(tool: A2aTool, arguments: String, contextId: String, client: A2aClientImpl)(implicit
      ec: ExecutionContext): Future[String] = {
    val message =
      try Right(toMessage(arguments))
      catch { case NonFatal(e) => Left(s"Error: the tool call could not be sent: ${e.getMessage}") }
    message match {
      case Left(error) => Future.successful(error)
      case Right(msg) =>
        val sent =
          try client
            .forAgent(tool.agent.nameOrCardUrl, tool.agent.timeout)
            .send(tool.skillId, msg)
            .inContext(contextId)
            .invokeAsync()
            .asScala
          catch { case NonFatal(e) => Future.failed(e) }
        sent.map(replyText).recover { case NonFatal(e) =>
          log.debug("The call of tool [{}] to remote A2A agent [{}] failed", tool.name, tool.agent.nameOrCardUrl, e)
          e match {
            case clientError: A2aClientException => s"Error: ${clientError.getMessage}"
            case other => s"Error: the call to remote agent [${tool.agent.nameOrCardUrl}] failed: ${other.getMessage}"
          }
        }
    }
  }

  private def toMessage(arguments: String): A2aMessage = {
    val json = mapper.readTree(arguments)
    val text = Option(json.get("message")).filter(_.isTextual).map(_.asText()).filterNot(_.isBlank)
    val dataPart = Option(json.get("data")).filterNot(_.isNull).map { data =>
      if (!data.isObject) throw new IllegalArgumentException("the data argument must be a JSON object")
      A2aPart.data(mapper.convertValue(data, classOf[java.util.Map[String, Object]]))
    }
    val textPart =
      A2aPart.text(text.getOrElse(throw new IllegalArgumentException("the message argument is missing or empty")))
    new A2aMessage(null, null, null, A2aRole.USER, (textPart +: dataPart.toSeq).asJava, null, null, null)
  }

  def replyText(response: A2aSendMessageResponse): String =
    if (response.message() != null) partsText(response.message().parts().asScala.toSeq)
    else taskText(response.task())

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
      .map { part =>
        if (part.text() != null) part.text()
        else if (part.data() != null) mapper.writeValueAsString(part.data())
        else if (part.url() != null) s"[file ${fileLabel(part)}: ${part.url()}]"
        else s"[file ${fileLabel(part)}, content not shown]"
      }
      .mkString("\n")

  private def fileLabel(part: A2aPart): String =
    Seq(Option(part.filename()), Option(part.mediaType()).map(t => s"($t)")).flatten.mkString(" ") match {
      case ""    => "without a name"
      case label => label
    }
}
