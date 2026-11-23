/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.impl.agent.GuardrailProvider.AgentGuardrails
import akka.javasdk.impl.agent.autonomous.AgentDefinitionImpl
import akka.javasdk.impl.agent.task.TaskNotFoundCommandException
import akka.javasdk.impl.client.ComponentClientImpl
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi._
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.OpenTelemetry
import org.scalatest.wordspec.AnyWordSpecLike

class AutonomousAgentTaskOperationsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  private val serializer = new Serializer()

  // Replies to every command with the error that TaskEntity replies for a task that does not exist.
  private val notFoundEntityClient: EntityClient = new EntityClient {
    override def send(request: EntityRequest): Future[EntityReply] =
      Future.successful(
        new EntityReply(
          BytesPayload.empty,
          SpiMetadata.empty,
          Some(serializer.toBytes(new TaskNotFoundCommandException()))))

    override def notificationStream(request: EntityRequest): Source[EntityReply, NotUsed] =
      Source.empty
  }

  private val componentClients: ComponentClients = new ComponentClients {
    override def eventSourcedEntityClient: EntityClient = notFoundEntityClient
    override def keyValueEntityClient: EntityClient = null
    override def workFlowClient: EntityClient = null
    override def workflowClient: WorkflowClient = null
    override def viewClient: ViewClient = null
    override def timedActionClient: TimedActionClient = null
    override def timerClient: TimerClient = null
    override def agentClient: AgentClient = null
    override def autonomousAgentClient: AutonomousAgentClient = null
    override def objectStorage: SpiObjectStorage = null
    override def decisionModelClient: SpiDecisionModelClient = null
  }

  // The task operations use only the component client, the serializer and the actor system.
  private val taskOperations: SpiTaskOperations = {
    implicit val ec: ExecutionContext = system.executionContext
    val componentClient = ComponentClientImpl(componentClients, null, serializer, Map.empty, None, None)
    val tracerFactory = () => OpenTelemetry.noop().getTracer("test")
    val agentDefinition = AgentDefinitionImpl(
      instructions = "",
      modelProvider = null,
      toolInstancesOrClasses = List.empty[AnyRef].asJava,
      mcpTools = List.empty.asJava,
      requestGuardrailClassNames = List.empty[String].asJava,
      responseGuardrailClassNames = List.empty[String].asJava,
      capabilities = List.empty.asJava,
      contentLoader = None)
    new AutonomousAgentImpl(
      componentId = "test-agent",
      instanceId = "test-instance",
      factory = _ => null,
      sdkExecutionContext = system.executionContext,
      tracerFactory = tracerFactory,
      serializer = serializer,
      regionInfo = null,
      componentClient = _ => componentClient,
      dependencyProvider = None,
      config = ConfigFactory.load(),
      eventLogClient = null,
      agentRegistry = null,
      _system = system,
      agentDefinition = agentDefinition,
      instructions = "",
      modelProvider = null,
      toolDescriptors = Seq.empty,
      mcpClientDescriptors = Seq.empty,
      agentGuardrails = new AgentGuardrails(Seq.empty, tracerFactory),
      capabilities = Seq.empty).taskOperations
  }

  private def assertTaskNotFound(call: Future[_]): Unit = {
    val failure = call.failed.futureValue
    failure shouldBe a[SpiTask.TaskNotFoundException]
    failure.asInstanceOf[SpiTask.TaskNotFoundException].taskId shouldBe "task-1"
  }

  "The task operations of an autonomous agent" should {

    "fail with the SPI not found exception for a task that does not exist" in {
      assertTaskNotFound(taskOperations.getTaskState("task-1", None))
      assertTaskNotFound(taskOperations.assignTask("task-1", "agent", None))
      assertTaskNotFound(taskOperations.startTask("task-1", None))
      assertTaskNotFound(taskOperations.completeTask("task-1", "{}", None))
      assertTaskNotFound(taskOperations.failTask("task-1", "reason", None))
      assertTaskNotFound(taskOperations.cancelTask("task-1", "reason", None))
      assertTaskNotFound(
        taskOperations.reassignTask("task-1", new SpiTask.ReassignRequest("other-agent", "context"), None))
    }
  }
}
