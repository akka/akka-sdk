/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.task.Task
import akka.javasdk.agent.task.TaskException
import akka.javasdk.agent.task.TaskNotification
import akka.javasdk.agent.task.TaskState
import akka.javasdk.agent.task.TaskStatus
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi._
import akka.stream.scaladsl.Source
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object TaskClientImplSpec {
  case class TestResult(value: String, score: Int)
}

class TaskClientImplSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with ScalaFutures {
  import TaskClientImplSpec._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  private implicit val ec: ExecutionContext = system.executionContext
  private val materializer = akka.stream.Materializer(system)

  private val serializer = {
    val s = new Serializer()
    s.registerTypeHints(classOf[TaskNotification.Completed])
    s.registerTypeHints(classOf[TaskNotification.Failed])
    s.registerTypeHints(classOf[TaskNotification.Cancelled])
    s
  }

  private val TEST_TASK: Task[TestResult] = Task
    .define("Test task")
    .description("A test task")
    .resultConformsTo(classOf[TestResult])

  private def taskState(status: TaskStatus, result: String = null, failureReason: String = null): TaskState =
    new TaskState(
      "test-task",
      "Test task",
      "description",
      "instructions",
      status,
      classOf[TestResult].getName,
      result,
      failureReason,
      java.util.List.of(),
      null,
      java.util.List.of(),
      java.util.List.of())

  private def entityReplyFor(state: TaskState): EntityReply =
    new EntityReply(serializer.toBytes(state), SpiMetadata.empty, None)

  private def entityReplyFor(notification: TaskNotification): EntityReply =
    new EntityReply(serializer.toBytes(notification), SpiMetadata.empty, None)

  private def mockEntityClient(
      stateResponse: TaskState,
      notificationSource: Source[EntityReply, _] = Source.never): EntityClient =
    new EntityClient {
      override def send(request: EntityRequest): Future[EntityReply] =
        request.methodName match {
          case "GetState" => Future.successful(entityReplyFor(stateResponse))
          case other      => Future.failed(new IllegalArgumentException(s"Unexpected method: $other"))
        }

      override def notificationStream(request: EntityRequest): Source[EntityReply, NotUsed] =
        notificationSource.mapMaterializedValue(_ => NotUsed)
    }

  private def mockComponentClients(entityClient: EntityClient): ComponentClients =
    new ComponentClients {
      override def eventSourcedEntityClient: EntityClient = entityClient
      override def keyValueEntityClient: EntityClient = null
      override def workFlowClient: EntityClient = null
      override def viewClient: ViewClient = null
      override def timedActionClient: TimedActionClient = null
      override def timerClient: TimerClient = null
      override def agentClient: AgentClient = null
      override def autonomousAgentClient: AutonomousAgentClient = null
    }

  private def createClient(entityClient: EntityClient): TaskClientImpl =
    new TaskClientImpl("test-task", mockComponentClients(entityClient), serializer, None, materializer)

  private def resultFuture(client: TaskClientImpl): Future[TestResult] =
    client.resultAsync(TEST_TASK).asScala

  private def inProgressClientWithNotification(): (Promise[EntityReply], Future[TestResult]) = {
    val notificationPromise = Promise[EntityReply]()
    val client =
      createClient(mockEntityClient(taskState(TaskStatus.IN_PROGRESS), Source.future(notificationPromise.future)))
    (notificationPromise, resultFuture(client))
  }

  private def failedWith[E <: TaskException](future: Future[TestResult])(implicit tag: reflect.ClassTag[E]): E =
    future.failed.futureValue match {
      case e: E  => e
      case other => fail(s"Expected ${tag.runtimeClass.getSimpleName} but got ${other.getClass.getName}")
    }

  "TaskClientImpl resultAsync" should {

    "return result for already completed task" in {
      val client =
        createClient(mockEntityClient(taskState(TaskStatus.COMPLETED, result = """{"value":"done","score":42}""")))

      val result = resultFuture(client).futureValue
      result.value shouldBe "done"
      result.score shouldBe 42
    }

    "throw TaskException.Failed for already failed task" in {
      val client = createClient(mockEntityClient(taskState(TaskStatus.FAILED, failureReason = "something broke")))

      val ex = failedWith[TaskException.Failed](resultFuture(client))
      ex.reason() shouldBe "something broke"
      ex.taskId() shouldBe "test-task"
    }

    "throw TaskException.Cancelled for already cancelled task" in {
      val client =
        createClient(mockEntityClient(taskState(TaskStatus.CANCELLED, failureReason = "no longer needed")))

      val ex = failedWith[TaskException.Cancelled](resultFuture(client))
      ex.reason() shouldBe "no longer needed"
    }

    "return result via notification when task completes after subscription" in {
      val (notificationPromise, future) = inProgressClientWithNotification()

      Thread.sleep(200)
      future.isCompleted shouldBe false

      notificationPromise.success(
        entityReplyFor(new TaskNotification.Completed("test-task", """{"value":"via notification","score":7}""")))

      val result = future.futureValue
      result.value shouldBe "via notification"
      result.score shouldBe 7
    }

    "throw TaskException.Failed via notification when task fails after subscription" in {
      val (notificationPromise, future) = inProgressClientWithNotification()

      notificationPromise.success(entityReplyFor(new TaskNotification.Failed("test-task", "failed via notification")))

      val ex = failedWith[TaskException.Failed](future)
      ex.reason() shouldBe "failed via notification"
    }

    "throw TaskException.Cancelled via notification when task is cancelled after subscription" in {
      val (notificationPromise, future) = inProgressClientWithNotification()

      notificationPromise.success(
        entityReplyFor(new TaskNotification.Cancelled("test-task", "cancelled via notification")))

      val ex = failedWith[TaskException.Cancelled](future)
      ex.reason() shouldBe "cancelled via notification"
    }
  }
}
