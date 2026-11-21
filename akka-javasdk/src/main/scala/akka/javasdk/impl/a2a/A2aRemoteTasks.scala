/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._
import scala.util.control.NonFatal

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.a2a.A2aArtifact
import akka.javasdk.a2a.A2aMessage
import akka.javasdk.a2a.A2aRole
import akka.javasdk.a2a.A2aPart
import akka.javasdk.a2a.A2aSendMessageResponse
import akka.javasdk.a2a.A2aStreamResponse
import akka.javasdk.a2a.A2aTask
import akka.javasdk.a2a.A2aTaskArtifactUpdate
import akka.javasdk.a2a.A2aTaskState
import akka.javasdk.a2a.A2aTaskStatus
import akka.javasdk.a2a.A2aTaskStatusUpdate
import akka.javasdk.a2a.A2aTaskUpdate
import akka.javasdk.client.ComponentClient
import akka.javasdk.client.ComponentDeferredMethodRef1
import akka.javasdk.impl.a2a.A2aRemoteTaskSubscriptionEntity.Target
import akka.javasdk.impl.client.ComponentMethodRefImpl
import akka.javasdk.impl.client.DeferredCallImpl
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.EntityRequest
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.WorkflowType
import akka.runtime.sdk.spi.{ WorkflowClient => RuntimeWorkflowClient }
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 *
 * Follows remote A2A tasks for workflow commands: registers push notification configurations or starts polling,
 * receives the push notifications, and delivers each update to the command once.
 *
 * @param enabled
 *   true when the service has the components that follow remote tasks
 * @param pushPublicUrl
 *   the public base URL of this service for push notifications, when one is configured and the push endpoint is
 *   registered
 */
@InternalApi
private[akka] final class A2aRemoteTasks(
    workflowClient: RuntimeWorkflowClient,
    serializer: Serializer,
    componentClient: () => ComponentClient,
    remoteAgents: A2aRemoteAgents,
    pushPublicUrl: Option[String],
    val enabled: Boolean,
    sanitizersOf: String => A2aReplySanitizer = _ => A2aRemoteTasks.NoSanitizers)(implicit ec: ExecutionContext) {
  import A2aRemoteTasks._

  /**
   * Starts following a task that has not ended: registers a push notification configuration when the remote agent and
   * this service support push, and starts polling otherwise. The updates in the task as given are taken as delivered.
   * The following ends after the follow timeout.
   */
  def follow(
      client: RemoteA2aAgentClientImpl,
      remote: A2aRemoteRef,
      target: Target,
      task: A2aTask,
      pollInterval: FiniteDuration,
      followTimeout: FiniteDuration): Future[Done] = {
    if (!enabled)
      return Future.failed(
        new IllegalStateException(
          "Following a remote A2A task needs the SDK's remote task components, which a service has when a " +
          "component injects A2aClient or the service has an agent"))
    val subscriptionId = subscriptionIdFor(target, client.cardUrl, task.id())
    val initialKeys = updatesOf(task).map(_._1)
    client.card().invokeAsync().asScala.flatMap { card =>
      val pushSupported =
        Option(card.capabilities()).flatMap(c => Option(c.pushNotifications())).exists(_.booleanValue())
      val pushUrl = pushPublicUrl.filter(_ => pushSupported).map(base => s"$base${pushPath(subscriptionId)}")
      val token = pushUrl.map(_ => newToken())
      A2aRemoteTaskCalls
        .subscribe(
          componentClient(),
          subscriptionId,
          new A2aRemoteTaskSubscriptionEntity.Subscribe(
            target,
            task.id(),
            token.map(tokenHash).orNull,
            initialKeys.asJava))
        .asScala
        .flatMap { tokenStored =>
          (pushUrl, token) match {
            case (Some(url), Some(t)) if tokenStored.booleanValue() =>
              client
                .createPushConfig(task.id(), url, t)
                .map(_ => true)
                .recover { case NonFatal(e) =>
                  log.warn(
                    "Remote A2A agent did not accept a push notification configuration for task [{}], polling instead: {}",
                    task.id(),
                    e.getMessage)
                  false
                }
            // The configuration was registered when the task was first followed
            case (Some(_), _) => Future.successful(true)
            case _            => Future.successful(false)
          }
        }
        .flatMap { usePush =>
          // With push, one poll catches a change made before the configuration was registered, and the poll workflow
          // then waits for the deadline
          def start(once: Boolean) =
            startPoll(
              new A2aRemoteTaskPollWorkflow.State(
                subscriptionId,
                remote,
                task.id(),
                task.contextId(),
                pollInterval.toMillis,
                once,
                0,
                System.currentTimeMillis() + followTimeout.toMillis,
                followTimeout.toMillis))
          if (usePush) start(once = true)
          else if (settles(task)) Future.successful(Done)
          else start(once = false)
        }
    }
  }

  private def startPoll(poll: A2aRemoteTaskPollWorkflow.State): Future[Done] =
    A2aRemoteTaskCalls.startPoll(componentClient(), UUID.randomUUID().toString, poll).asScala

  /**
   * Ends the following of a task at its deadline: unless an update that ends the task was delivered, the workflow
   * command receives a status update whose `followTimedOut()` is true, and no later update is delivered.
   */
  def followTimedOut(
      subscriptionId: String,
      taskId: String,
      contextId: String,
      followTimeout: java.time.Duration): CompletionStage[Done] =
    getSubscription(subscriptionId).flatMap { state =>
      if (state.target() == null || state.ended()) Future.successful(Done)
      else {
        val update = followTimedOutUpdate(taskId, contextId, followTimeout)
        callWorkflow(state.target(), update).flatMap { _ =>
          A2aRemoteTaskCalls
            .delivered(
              componentClient(),
              subscriptionId,
              new A2aRemoteTaskSubscriptionEntity.Delivered(FollowTimedOutKey, true))
            .asScala
        }
      }
    }.asJava

  /**
   * Reads the task once and delivers its changes.
   *
   * @return
   *   true to poll again, false when the task has ended or needs input
   */
  def poll(subscriptionId: String, remote: A2aRemoteRef, taskId: String): CompletionStage[java.lang.Boolean] =
    clientFor(remote)
      .getTask(taskId)
      .invokeAsync()
      .asScala
      .flatMap { task =>
        deliver(subscriptionId, updatesOf(task)).map(_ => java.lang.Boolean.valueOf(!settles(task)))
      }
      .asJava

  /**
   * Handles a push notification.
   *
   * @return
   *   the HTTP status of the answer
   */
  def receivePush(subscriptionId: String, token: Optional[String], body: Array[Byte]): CompletionStage[Integer] =
    getSubscription(subscriptionId)
      .flatMap { state =>
        if (!tokenMatches(Option(state.tokenHash()), token.toScala)) {
          log.debug("Refused a push notification for subscription [{}] with an unknown token", subscriptionId)
          Future.successful(Unauthorized)
        } else
          parsePush(body) match {
            case None => Future.successful(BadRequest)
            case Some(updates) if updates.exists(_._2.taskId() != state.taskId()) =>
              log.debug("Refused a push notification for subscription [{}] about another task", subscriptionId)
              Future.successful(BadRequest)
            case Some(_) if state.ended() => Future.successful(Ok)
            case Some(updates) =>
              deliverTo(subscriptionId, state, updates).map(_ => Ok).recover { case NonFatal(e) =>
                log.warn(
                  "Delivering a push notification of remote A2A task [{}] to workflow [{}] failed",
                  state.taskId(),
                  state.target().workflowId(),
                  e)
                ServiceUnavailable
              }
          }
      }
      .map(Integer.valueOf)
      .asJava

  /**
   * Starts a [[RemoteA2aTaskWorkflow]] that follows a task a tool call handed off, and starts following the task for
   * it.
   */
  def handOff(
      client: RemoteA2aAgentClientImpl,
      remote: A2aRemoteRef,
      task: A2aTask,
      sessionId: String,
      agentComponentId: String,
      toolName: String,
      pollInterval: FiniteDuration,
      followTimeout: FiniteDuration): Future[Done] = {
    val workflowId =
      UUID
        .nameUUIDFromBytes(s"$sessionId\n${client.cardUrl}\n${task.id()}".getBytes(StandardCharsets.UTF_8))
        .toString
    A2aRemoteTaskCalls
      .startHandOff(
        componentClient(),
        workflowId,
        new RemoteA2aTaskWorkflow.State(sessionId, agentComponentId, toolName, remote, task.id(), null, false))
      .asScala
      .flatMap(_ =>
        follow(
          client,
          remote,
          new Target(HandOffComponentId, HandOffMethodName, workflowId),
          task,
          pollInterval,
          followTimeout))
  }

  /**
   * The text of the outcome of a handed off task, read from the remote agent and masked with the tool-result sanitizers
   * of the agent that handed it off.
   */
  def handOffOutcome(remote: A2aRemoteRef, taskId: String, agentComponentId: String): CompletionStage[String] =
    clientFor(remote)
      .getTask(taskId)
      .invokeAsync()
      .asScala
      .flatMap(task => sanitizersOf(agentComponentId).task(task))
      .map(task => A2aAgentTools.replyText(A2aSendMessageResponse.of(task)))
      .asJava

  /** Masks text with the tool-result sanitizers of an agent. */
  def sanitizeForAgent(agentComponentId: String, text: String): CompletionStage[String] =
    sanitizersOf(agentComponentId).text(text).asJava

  private def clientFor(remote: A2aRemoteRef) =
    new A2aClientImpl(remoteAgents, Nil, Nil, Some(this)).forTarget(targetOf(remote), None)

  private def getSubscription(subscriptionId: String): Future[A2aRemoteTaskSubscriptionEntity.State] =
    A2aRemoteTaskCalls.subscription(componentClient(), subscriptionId).asScala

  private def deliver(subscriptionId: String, updates: Seq[(String, A2aTaskUpdate)]): Future[Done] =
    getSubscription(subscriptionId).flatMap { state =>
      if (state.target() == null)
        Future.failed(new IllegalStateException(s"No subscription [$subscriptionId] for remote A2A task"))
      else deliverTo(subscriptionId, state, updates)
    }

  // Each update is delivered and then recorded, so a failure between the two delivers the update again
  private def deliverTo(
      subscriptionId: String,
      state: A2aRemoteTaskSubscriptionEntity.State,
      updates: Seq[(String, A2aTaskUpdate)]): Future[Done] = {
    val delivered = state.deliveredKeys().asScala.toSet
    updates.filterNot { case (key, _) => delivered.contains(key) }.foldLeft(Future.successful(Done: Done)) {
      case (previous, (key, update)) =>
        previous.flatMap { _ =>
          callWorkflow(state.target(), update).flatMap { _ =>
            A2aRemoteTaskCalls
              .delivered(
                componentClient(),
                subscriptionId,
                new A2aRemoteTaskSubscriptionEntity.Delivered(key, ends(update)))
              .asScala
          }
        }
    }
  }

  // An update is masked with the tool-result sanitizers of the workflow that receives it. The hand-off workflow masks
  // the outcome it writes for its agent instead.
  private def callWorkflow(target: Target, update: A2aTaskUpdate): Future[Done] = {
    val masked =
      if (target.componentId() == HandOffComponentId) Future.successful(update)
      else sanitizersOf(target.componentId()).update(update)
    masked.flatMap { sanitized =>
      workflowClient
        .send(
          new EntityRequest(
            target.componentId(),
            target.workflowId(),
            target.methodName(),
            serializer.toBytes(sanitized),
            SpiMetadata.empty))
        .map { reply =>
          reply.exceptionPayload.foreach(payload => throw serializer.json.exceptionFromBytes(payload))
          Done
        }
    }
  }

  private def parsePush(body: Array[Byte]): Option[Seq[(String, A2aTaskUpdate)]] =
    try {
      val tree: JsonNode = RemoteA2aAgentState.mapper.readTree(body)
      if (tree == null || !tree.isObject) None
      else if (Seq("task", "message", "statusUpdate", "artifactUpdate").exists(tree.has))
        Some(RemoteA2aAgentState.mapper.treeToValue(tree, classOf[A2aStreamResponse]) match {
          case t: A2aStreamResponse.Task           => updatesOf(t.task())
          case s: A2aStreamResponse.StatusUpdate   => Seq(keyOf(s.statusUpdate()) -> s.statusUpdate())
          case a: A2aStreamResponse.ArtifactUpdate => Seq(keyOf(a.artifactUpdate()) -> a.artifactUpdate())
          case _: A2aStreamResponse.Message        => Seq.empty
        })
      // Some agents push the task itself rather than a StreamResponse
      else if (tree.has("id") && tree.has("status"))
        Some(updatesOf(RemoteA2aAgentState.mapper.treeToValue(tree, classOf[A2aTask])))
      else None
    } catch {
      case NonFatal(e) =>
        log.debug("Refused a push notification that is not a valid A2A update", e)
        None
    }
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aRemoteTasks {
  private val log = LoggerFactory.getLogger(classOf[A2aRemoteTasks])
  private val random = new SecureRandom()

  private val Ok = 200
  private val BadRequest = 400
  private val Unauthorized = 401
  private val ServiceUnavailable = 503

  val HandOffComponentId = "akka-a2a-remote-task-hand-off"
  val HandOffMethodName = "OnUpdate"

  private val FollowTimedOutKey = "follow-timed-out"

  private[a2a] val NoSanitizers: A2aReplySanitizer = new A2aReplySanitizer(Nil, null)(ExecutionContext.parasitic)

  /** The status update that tells a workflow that the following of a remote task ended before the task did. */
  def followTimedOutUpdate(taskId: String, contextId: String, followTimeout: java.time.Duration): A2aTaskStatusUpdate =
    new A2aTaskStatusUpdate(
      taskId,
      contextId,
      new A2aTaskStatus(
        A2aTaskState.UNSPECIFIED,
        A2aMessage
          .user(A2aPart.text(
            s"The remote task was followed for ${scala.concurrent.duration.FiniteDuration(followTimeout.toMillis, "ms").toCoarsest} without ending or asking for input. No more updates are delivered."))
          .withRole(A2aRole.AGENT),
        java.time.Instant.now()),
      java.util.Map.of(A2aTaskStatusUpdate.FOLLOW_TIMED_OUT, java.lang.Boolean.TRUE))

  // Compared with a presented token when the subscription has none, so that an unknown subscription takes as long to
  // refuse as a wrong token
  private val NoTokenHash = new Array[Byte](32)

  def pushPath(subscriptionId: String): String = s"${A2aPushReceiverEndpoint.PATH}/$subscriptionId"

  /** The workflow, command and workflow id that a component client method reference names. */
  def workflowTarget(workflowCommand: ComponentDeferredMethodRef1[A2aTaskUpdate, _]): Target =
    workflowCommand match {
      case ref: ComponentMethodRefImpl[_, _] =>
        val placeholder: A2aTaskUpdate = new A2aTaskStatusUpdate("", "", null, null)
        ref.asInstanceOf[ComponentMethodRefImpl[A2aTaskUpdate, Any]].deferred(placeholder) match {
          case call: DeferredCallImpl[_, _] if call.componentType == WorkflowType && call.entityId.isDefined =>
            new Target(call.componentId, call.methodName, call.entityId.get)
          case _ => notAWorkflowCommand()
        }
      case _ => notAWorkflowCommand()
    }

  private def notAWorkflowCommand(): Nothing =
    throw new IllegalArgumentException(
      "notifyWorkflow takes a method reference of a workflow command from the component client, for example " +
      "componentClient.forWorkflow(id).method(MyWorkflow::onUpdate)")

  def subscriptionIdFor(target: Target, cardUrl: String, taskId: String): String =
    UUID
      .nameUUIDFromBytes(
        Seq(target.componentId(), target.methodName(), target.workflowId(), A2aRemoteAgents.normalized(cardUrl), taskId)
          .mkString("\n")
          .getBytes(StandardCharsets.UTF_8))
      .toString

  def refOf(target: A2aRemoteTarget): A2aRemoteRef = target match {
    case A2aRemoteTarget.Named(name)      => new A2aRemoteRef("named", name)
    case A2aRemoteTarget.CardUrl(url)     => new A2aRemoteRef("card-url", url)
    case A2aRemoteTarget.Service(service) => new A2aRemoteRef("service", service)
  }

  def targetOf(ref: A2aRemoteRef): A2aRemoteTarget = ref.kind() match {
    case "named"    => A2aRemoteTarget.named(ref.value())
    case "card-url" => A2aRemoteTarget.cardUrl(ref.value())
    case "service"  => A2aRemoteTarget.service(ref.value())
    case other      => throw new IllegalArgumentException(s"Unknown kind of remote A2A agent [$other]")
  }

  private def state(task: A2aTask): A2aTaskState =
    Option(task.status()).flatMap(s => Option(s.state())).getOrElse(A2aTaskState.UNSPECIFIED)

  /** True when the task has ended or waits for the client. */
  def settles(task: A2aTask): Boolean = settles(state(task))

  def settles(state: A2aTaskState): Boolean =
    state.terminal() || state == A2aTaskState.INPUT_REQUIRED || state == A2aTaskState.AUTH_REQUIRED

  private def ends(update: A2aTaskUpdate): Boolean = update match {
    case s: A2aTaskStatusUpdate => Option(s.status()).flatMap(st => Option(st.state())).exists(_.terminal())
    case _                      => false
  }

  /** The updates that a full task stands for: each artifact, then the status. */
  def updatesOf(task: A2aTask): Seq[(String, A2aTaskUpdate)] = {
    val artifacts = task.artifacts().asScala.toSeq.map { artifact =>
      val update = new A2aTaskArtifactUpdate(task.id(), task.contextId(), artifact, false, true, null)
      keyOf(update) -> (update: A2aTaskUpdate)
    }
    val status = Option(task.status()).map { status =>
      val update = new A2aTaskStatusUpdate(task.id(), task.contextId(), status, null)
      keyOf(update) -> (update: A2aTaskUpdate)
    }
    artifacts ++ status
  }

  /**
   * The key that identifies an update for de-duplication: a status by its state, timestamp and message id, and an
   * artifact by its id and a hash of its content.
   */
  def keyOf(update: A2aTaskUpdate): String = update match {
    case s: A2aTaskStatusUpdate =>
      val status = Option(s.status())
      Seq(
        "status",
        status.flatMap(st => Option(st.state())).getOrElse(A2aTaskState.UNSPECIFIED).wireName(),
        status.flatMap(st => Option(st.timestamp())).map(_.toString).getOrElse(""),
        status.flatMap(st => Option(st.message())).flatMap(m => Option(m.messageId())).getOrElse(""))
        .mkString("|")
    case a: A2aTaskArtifactUpdate =>
      Seq("artifact", Option(a.artifact()).map(_.artifactId()).orNull, a.append().toString, contentHash(a.artifact()))
        .mkString("|")
  }

  private def contentHash(artifact: A2aArtifact): String =
    if (artifact == null) ""
    else
      Base64.getUrlEncoder
        .withoutPadding()
        .encodeToString(sha256(RemoteA2aAgentState.mapper.writeValueAsBytes(artifact.parts())))
        .take(22)

  /** The outcome of a task as its last status tells it, when the task itself cannot be read. */
  def statusOutcome(status: A2aTaskStatusUpdate): String = {
    val state = Option(status).flatMap(s => Option(s.status())).flatMap(s => Option(s.state()))
    val text = Option(status)
      .flatMap(s => Option(s.status()))
      .flatMap(s => Option(s.message()))
      .map(_.parts().asScala.collect { case t: A2aPart.Text => t.text() }.mkString("\n"))
      .filterNot(_.isBlank)
    s"The remote task is in state ${state.getOrElse(A2aTaskState.UNSPECIFIED).wireName()}" + text.fold("")(t => s": $t")
  }

  def newToken(): String = {
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }

  def tokenHash(token: String): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(sha256(token.getBytes(StandardCharsets.UTF_8)))

  /** Compares in constant time, whether or not the subscription has a token. */
  def tokenMatches(storedHash: Option[String], presented: Option[String]): Boolean = {
    val presentedHash = sha256(presented.getOrElse("").getBytes(StandardCharsets.UTF_8))
    val expected =
      storedHash.flatMap(h =>
        try Some(Base64.getUrlDecoder.decode(h))
        catch { case NonFatal(_) => None })
    val equal = MessageDigest.isEqual(expected.getOrElse(NoTokenHash), presentedHash)
    equal && expected.isDefined && presented.exists(_.nonEmpty)
  }

  private def sha256(bytes: Array[Byte]): Array[Byte] = MessageDigest.getInstance("SHA-256").digest(bytes)
}
