/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.concurrent.CompletionException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.javasdk.CommandException
import akka.javasdk.impl.agent.task.TaskNotFoundCommandException
import akka.runtime.sdk.spi.SpiTask
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TaskNotFoundMappingSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.parasitic

  private def failureOf(call: => Future[Int]): Throwable =
    AutonomousAgentImpl.notFoundAsSpi("task-1")(call).failed.futureValue

  private def assertTaskNotFound(failure: Throwable): Unit = {
    failure shouldBe a[SpiTask.TaskNotFoundException]
    failure.asInstanceOf[SpiTask.TaskNotFoundException].taskId shouldBe "task-1"
  }

  "AutonomousAgentImpl.notFoundAsSpi" should {

    "fail with the SPI not found exception for the not found reply" in {
      assertTaskNotFound(failureOf(Future.failed(new TaskNotFoundCommandException())))
    }

    "fail with the SPI not found exception for the not found reply in a CompletionException" in {
      assertTaskNotFound(failureOf(Future.failed(new CompletionException(new TaskNotFoundCommandException()))))
    }

    "keep any other failure" in {
      val other = new CommandException("Task can only be started when ASSIGNED")
      failureOf(Future.failed(other)) shouldBe theSameInstanceAs(other)

      val wrapped = new CompletionException(other)
      failureOf(Future.failed(wrapped)) shouldBe theSameInstanceAs(wrapped)
    }

    "keep a result" in {
      AutonomousAgentImpl.notFoundAsSpi("task-1")(Future.successful(42)).futureValue shouldBe 42
    }
  }
}
