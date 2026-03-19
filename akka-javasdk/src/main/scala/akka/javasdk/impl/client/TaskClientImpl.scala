/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.UUID
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.task.Task
import akka.javasdk.agent.task.TaskAttachment
import akka.javasdk.agent.task.TaskDefinition
import akka.javasdk.agent.task.TaskEntity
import akka.javasdk.agent.task.TaskSnapshot
import akka.javasdk.agent.task.TaskState
import akka.javasdk.client.TaskClient
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.EntityRequest
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.{ ComponentClients => RuntimeComponentClients }
import akka.util.ByteString
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class TaskClientImpl[R](
    taskDefinition: Option[TaskDefinition[R]],
    runtimeComponentClients: RuntimeComponentClients,
    serializer: Serializer,
    callMetadata: Option[Metadata])(implicit ec: ExecutionContext)
    extends TaskClient[R] {

  private val log = LoggerFactory.getLogger(classOf[TaskClientImpl[_]])

  private val TaskEntityComponentId = "akka-task"

  private def spiMetadata: SpiMetadata = callMetadata.fold(SpiMetadata.empty)(MetadataImpl.toSpi)

  override def createAsync(task: Task[R]): CompletionStage[String] = {
    val taskId = UUID.randomUUID().toString
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

  override def getAsync(taskId: String): CompletionStage[TaskSnapshot[R]] = {
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
              deserializeResult(taskState),
              taskState.failureReason())
        }
      }
      .asJava
  }

  private def deserializeResult(taskState: TaskState): R = {
    if (taskState.result() == null) {
      null.asInstanceOf[R]
    } else {
      val resultType = taskDefinition
        .getOrElse(
          throw new IllegalStateException(
            "TaskClient was not created with a task definition; cannot deserialize result"))
        .resultType()
      if (resultType == classOf[String]) {
        // String results are stored as raw text by the runtime (not JSON-encoded)
        taskState.result().asInstanceOf[R]
      } else {
        // Typed results are stored as JSON — deserialize via the serializer
        val resultBytes = new BytesPayload(ByteString.fromString(taskState.result()), "application/json")
        serializer.fromBytes[R](resultType.asInstanceOf[java.lang.reflect.Type], resultBytes)
      }
    }
  }
}
