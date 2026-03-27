/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.task.Task
import akka.javasdk.agent.task.TaskEntity
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

  case class RecordedCommand(methodName: String, payload: BytesPayload) {
    def payloadAs[T](implicit ct: reflect.ClassTag[T], serializer: Serializer): T =
      serializer.fromBytes(ct.runtimeClass.asInstanceOf[Class[T]], payload)
  }
}

class TaskClientImplSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with ScalaFutures {
  import TaskClientImplSpec._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  private implicit val ec: ExecutionContext = system.executionContext
  private val materializer = akka.stream.Materializer(system)

  private implicit val serializer: Serializer = {
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

  private val STRING_TASK: Task[String] = Task
    .define("String task")
    .description("A string task")

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

  private def successReply: EntityReply =
    new EntityReply(BytesPayload.empty, SpiMetadata.empty, None)

  /** Mock EntityClient that records commands and responds to GetState. */
  private class MockEntityClient(stateResponse: TaskState, notificationSource: Source[EntityReply, _] = Source.never)
      extends EntityClient {

    val commands: mutable.Buffer[RecordedCommand] = mutable.Buffer.empty

    override def send(request: EntityRequest): Future[EntityReply] = {
      commands += RecordedCommand(request.methodName, request.payload)
      request.methodName match {
        case "GetState" => Future.successful(entityReplyFor(stateResponse))
        case _          => Future.successful(successReply)
      }
    }

    override def notificationStream(request: EntityRequest): Source[EntityReply, NotUsed] =
      notificationSource.mapMaterializedValue(_ => NotUsed)

    def lastCommand: RecordedCommand = commands.last
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

  private def mockEntityClient(
      stateResponse: TaskState,
      notificationSource: Source[EntityReply, _] = Source.never): MockEntityClient =
    new MockEntityClient(stateResponse, notificationSource)

  private def failedWith[E <: TaskException](future: Future[TestResult])(implicit tag: reflect.ClassTag[E]): E =
    future.failed.futureValue match {
      case e: E  => e
      case other => fail(s"Expected ${tag.runtimeClass.getSimpleName} but got ${other.getClass.getName}")
    }

  "TaskClientImpl createAsync" should {

    "send Create command with task definition" in {
      val mock = mockEntityClient(taskState(TaskStatus.PENDING))
      val client = createClient(mock)

      val taskId = client.createAsync(TEST_TASK.instructions("do something")).asScala.futureValue
      taskId shouldBe "test-task"

      val createCmd = mock.commands.find(_.methodName == "Create").get
      val request = createCmd.payloadAs[TaskEntity.CreateRequest]
      request.name() shouldBe "Test task"
      request.description() shouldBe "A test task"
      request.instructions() shouldBe "do something"
    }
  }

  "TaskClientImpl assignAsync" should {

    "send Assign command with assignee" in {
      val mock = mockEntityClient(taskState(TaskStatus.PENDING))
      val client = createClient(mock)

      client.assignAsync("reviewer@example.com").asScala.futureValue

      val assignCmd = mock.commands.find(_.methodName == "Assign").get
      val assignee = assignCmd.payloadAs[String]
      assignee shouldBe "reviewer@example.com"
    }
  }

  "TaskClientImpl completeAsync" should {

    "send Complete command with typed result serialized as JSON" in {
      val mock = mockEntityClient(taskState(TaskStatus.ASSIGNED))
      val client = createClient(mock)

      client.completeAsync(TEST_TASK, new TestResult("done", 42)).asScala.futureValue

      val completeCmd = mock.commands.find(_.methodName == "Complete").get
      val resultJson = completeCmd.payloadAs[String]
      resultJson should include("\"value\":\"done\"")
      resultJson should include("\"score\":42")
    }

    "send Complete command with string result directly" in {
      val mock = mockEntityClient(taskState(TaskStatus.ASSIGNED))
      val client = createClient(mock)

      client.completeAsync(STRING_TASK, "plain text result").asScala.futureValue

      val completeCmd = mock.commands.find(_.methodName == "Complete").get
      val resultJson = completeCmd.payloadAs[String]
      resultJson shouldBe "plain text result"
    }
  }

  "TaskClientImpl failAsync" should {

    "send Fail command with reason" in {
      val mock = mockEntityClient(taskState(TaskStatus.ASSIGNED))
      val client = createClient(mock)

      client.failAsync("not good enough").asScala.futureValue

      val failCmd = mock.commands.find(_.methodName == "Fail").get
      val reason = failCmd.payloadAs[String]
      reason shouldBe "not good enough"
    }
  }

  "TaskClientImpl getAsync" should {

    "return task snapshot with typed result" in {
      val mock =
        mockEntityClient(taskState(TaskStatus.COMPLETED, result = """{"value":"done","score":42}"""))
      val client = createClient(mock)

      val snapshot = client.getAsync(TEST_TASK).asScala.futureValue
      snapshot.status() shouldBe TaskStatus.COMPLETED
      snapshot.result().value shouldBe "done"
      snapshot.result().score shouldBe 42
    }

    "return task snapshot with null result when pending" in {
      val mock = mockEntityClient(taskState(TaskStatus.PENDING))
      val client = createClient(mock)

      val snapshot = client.getAsync(TEST_TASK).asScala.futureValue
      snapshot.status() shouldBe TaskStatus.PENDING
      snapshot.result() shouldBe null
    }

    "return task snapshot with failure reason" in {
      val mock = mockEntityClient(taskState(TaskStatus.FAILED, failureReason = "something broke"))
      val client = createClient(mock)

      val snapshot = client.getAsync(TEST_TASK).asScala.futureValue
      snapshot.status() shouldBe TaskStatus.FAILED
      snapshot.failureReason() shouldBe "something broke"
    }
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
