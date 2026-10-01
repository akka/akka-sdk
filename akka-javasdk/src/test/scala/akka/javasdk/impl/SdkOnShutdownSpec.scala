/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.Done
import akka.javasdk.ServiceSetup
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SdkOnShutdownSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val executor = Executors.newCachedThreadPool()
  private val blockingEc = ExecutionContext.fromExecutor(executor)

  override def afterAll(): Unit = executor.shutdownNow()

  "The onShutdown coordinated shutdown task" should {

    "not block the calling thread while the hook runs" in {
      val hookStarted = new CountDownLatch(1)
      val releaseHook = new CountDownLatch(1)
      val setup = new ServiceSetup {
        override def onShutdown(): Unit = {
          hookStarted.countDown()
          releaseHook.await(10, TimeUnit.SECONDS)
        }
      }

      val result = Sdk.onShutdownTask(setup, blockingEc)()

      hookStarted.await(3, TimeUnit.SECONDS) shouldBe true
      result.isCompleted shouldBe false

      releaseHook.countDown()
      Await.result(result, 3.seconds) shouldBe Done
    }

    "complete successfully when the hook throws" in {
      val setup = new ServiceSetup {
        override def onShutdown(): Unit = throw new RuntimeException("boom")
      }

      Await.result(Sdk.onShutdownTask(setup, blockingEc)(), 3.seconds) shouldBe Done
    }
  }
}
