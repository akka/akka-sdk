/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.task.Task
import akka.javasdk.agent.task.TaskAttachment
import akka.javasdk.agent.task.TaskDefinition
import akka.javasdk.agent.task.TaskEntity
import akka.javasdk.agent.task.TaskException
import akka.javasdk.agent.task.TaskNotification
import akka.javasdk.agent.task.TaskSnapshot
import akka.javasdk.agent.task.TaskState
import akka.javasdk.agent.task.TaskStatus
import akka.javasdk.client.TaskClient
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.EntityRequest
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.{ ComponentClients => RuntimeComponentClients }
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class TaskClientImpl(
    taskId: String,
    runtimeComponentClients: RuntimeComponentClients,
    serializer: Serializer,
    callMetadata: Option[Metadata],
    materializer: Materializer)(implicit ec: ExecutionContext)
    extends TaskClient {

  private val log = LoggerFactory.getLogger(classOf[TaskClientImpl])

  private val TaskEntityComponentId = "akka-task"

  private def spiMetadata: SpiMetadata = callMetadata.fold(SpiMetadata.empty)(MetadataImpl.toSpi)

  override def createAsync[R](task: Task[R]): CompletionStage[String] = {
    log.debug(
      "createTask: id=[{}] description=[{}] resultType=[{}]",
      taskId,
      task.description(),
      task.resultType().getName)
    val attachments = task.attachments().asScala.map(TaskAttachment.fromMessageContent(_)).asJava
    val createRequest = new TaskEntity.CreateRequest(
      task.name(),
      task.description(),
      task.instructions(),
      task.resultType().getName,
      task.dependencyTaskIds(),
      attachments)
    val createPayload = serializer.toBytes(createRequest)
    log.debug("createTask: sending Create to entity [{}], payload contentType=[{}]", taskId, createPayload.contentType)
    runtimeComponentClients.eventSourcedEntityClient
      .send(new EntityRequest(TaskEntityComponentId, taskId, "Create", createPayload, spiMetadata))
      .map { _ =>
        log.debug("createTask: entity [{}] created successfully", taskId)
        taskId
      }
      .asJava
  }

  override def getAsync[R](taskDefinition: TaskDefinition[R]): CompletionStage[TaskSnapshot[R]] = {
    log.debug("getTask: id=[{}]", taskId)
    runtimeComponentClients.eventSourcedEntityClient
      .send(new EntityRequest(TaskEntityComponentId, taskId, "GetState", BytesPayload.empty, spiMetadata))
      .map { reply =>
        reply.exceptionPayload match {
          case Some(exBytes) =>
            throw serializer.json.exceptionFromBytes(exBytes)
          case None =>
            val taskState = serializer.fromBytes[TaskState](classOf[TaskState], reply.payload)
            log.debug("getTask: id=[{}] status=[{}]", taskId, taskState.status())
            new TaskSnapshot[R](
              taskState.status(),
              taskState.description(),
              taskState.instructions(),
              deserializeResult(taskState, taskDefinition),
              taskState.failureReason())
        }
      }
      .asJava
  }

  override def resultAsync[R](taskDefinition: TaskDefinition[R]): CompletionStage[R] = {
    log.debug("resultAsync: subscribing to notifications for task [{}]", taskId)

    val notificationReq = new EntityRequest(TaskEntityComponentId, taskId, "", BytesPayload.empty, spiMetadata)

    // We need to handle the case where the task is already terminal when we subscribe.
    // The approach: subscribe to notifications first, then check current state. If already
    // terminal, return immediately. If not, wait for the notification.
    //
    // Ordering matters: the notification subscription must be established before we check
    // state, otherwise we could miss a completion that happens between the state check and
    // the subscription. We use mapMaterializedValue to detect when the notification stream
    // has been materialized, then chain the state check after that point.
    val subscribed = Promise[Done]()

    val notificationResult: Future[R] = runtimeComponentClients.eventSourcedEntityClient
      .notificationStream(notificationReq)
      .mapMaterializedValue { mat =>
        subscribed.success(Done)
        mat
      }
      .map { reply =>
        val notification = serializer.fromBytes(reply.payload)
        notification match {
          case completed: TaskNotification.Completed =>
            log.debug("resultAsync: task [{}] completed via notification", taskId)
            deserializeResultFromString(completed.result(), taskDefinition)
          case failed: TaskNotification.Failed =>
            log.debug("resultAsync: task [{}] failed via notification: {}", taskId, failed.reason())
            throw new TaskException.Failed(taskId, failed.reason())
          case cancelled: TaskNotification.Cancelled =>
            log.debug("resultAsync: task [{}] cancelled via notification: {}", taskId, cancelled.reason())
            throw new TaskException.Cancelled(taskId, cancelled.reason())
          case other =>
            throw new IllegalStateException(
              s"Unexpected notification type for task [$taskId]: ${other.getClass.getName}")
        }
      }
      .take(1)
      .runWith(Sink.head)(materializer)

    // After the subscription is in place, check if the task is already terminal.
    // If it is, return the result immediately. If not, wait for the notification.
    subscribed.future
      .flatMap { _ =>
        log.debug("resultAsync: subscription established for task [{}], checking current state", taskId)
        getAsync(taskDefinition).asScala
      }
      .flatMap { snapshot =>
        snapshot.status() match {
          case TaskStatus.COMPLETED =>
            log.debug("resultAsync: task [{}] already completed", taskId)
            Future.successful(snapshot.result())
          case TaskStatus.FAILED =>
            log.debug("resultAsync: task [{}] already failed", taskId)
            Future.failed(new TaskException.Failed(taskId, snapshot.failureReason()))
          case TaskStatus.CANCELLED =>
            log.debug("resultAsync: task [{}] already cancelled", taskId)
            Future.failed(new TaskException.Cancelled(taskId, snapshot.failureReason()))
          case _ =>
            log.debug(
              "resultAsync: task [{}] not yet terminal ({}), waiting for notification",
              taskId,
              snapshot.status())
            notificationResult
        }
      }
      .asJava
  }

  override def assignAsync(assignee: String): CompletionStage[Done] = {
    log.debug("assignTask: id=[{}] assignee=[{}]", taskId, assignee)
    val payload = serializer.toBytes(assignee)
    sendCommand("Assign", payload)
  }

  override def completeAsync[R](taskDefinition: TaskDefinition[R], result: R): CompletionStage[Done] = {
    log.debug("completeTask: id=[{}]", taskId)
    val resultJson = result match {
      case s: String => s
      case other     => serializer.json.toJsonString(other)
    }
    sendCommand("Complete", serializer.toBytes(resultJson))
  }

  override def failAsync(reason: String): CompletionStage[Done] = {
    log.debug("failTask: id=[{}] reason=[{}]", taskId, reason)
    sendCommand("Fail", serializer.toBytes(reason))
  }

  private def sendCommand(methodName: String, payload: BytesPayload): CompletionStage[Done] = {
    runtimeComponentClients.eventSourcedEntityClient
      .send(new EntityRequest(TaskEntityComponentId, taskId, methodName, payload, spiMetadata))
      .map { reply =>
        reply.exceptionPayload.foreach { exBytes =>
          throw serializer.json.exceptionFromBytes(exBytes)
        }
        Done.done()
      }
      .asJava
  }

  private def deserializeResult[R](taskState: TaskState, taskDefinition: TaskDefinition[R]): R = {
    deserializeResultFromString(taskState.result(), taskDefinition)
  }

  private def deserializeResultFromString[R](resultString: String, taskDefinition: TaskDefinition[R]): R = {
    if (resultString == null) {
      null.asInstanceOf[R]
    } else {
      val resultType = taskDefinition.resultType()
      if (resultType == classOf[String]) {
        // String results are stored as raw text by the runtime (not JSON-encoded)
        resultString.asInstanceOf[R]
      } else {
        // Typed results are stored as JSON — deserialize via the serializer
        val resultBytes = new BytesPayload(ByteString.fromString(resultString), "application/json")
        serializer.fromBytes[R](resultType.asInstanceOf[java.lang.reflect.Type], resultBytes)
      }
    }
  }
}
