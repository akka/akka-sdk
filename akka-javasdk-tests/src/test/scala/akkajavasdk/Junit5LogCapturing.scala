/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk

import akka.CapturingAppenderAccess
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.slf4j.LoggerFactory

class Junit5LogCapturing extends BeforeEachCallback with TestWatcher {
  // FIXME backport this to akka core Java testkit

  private val capturingAppender = CapturingAppenderAccess.capturingAppender

  private val myLogger = LoggerFactory.getLogger(classOf[Junit5LogCapturing])

  override def beforeEach(context: ExtensionContext): Unit = {
    myLogger.info(
      s"${Console.BLUE}Logging started for test [${context.getTestClass}: ${context.getTestMethod.get().getName}${Console.RESET}]")
  }

  override def testSuccessful(context: ExtensionContext): Unit = {
    myLogger.info(
      s"${Console.BLUE}Logging finished for test [${context.getTestClass}: ${context.getTestMethod.get().getName}] that was successful${Console.RESET}")
    capturingAppender.clear()
  }

  override def testFailed(context: ExtensionContext, cause: Throwable): Unit = {
    println(
      s"--> [${Console.BLUE}${context.getTestClass}: ${context.getTestMethod.get().getName}${Console.RESET}] " +
      s"Start of log messages of test that failed with ${cause.getMessage}")
    capturingAppender.flush()
    println(
      s"<-- [${Console.BLUE}${context.getTestClass}: ${context.getTestMethod.get().getName}${Console.RESET}] " +
      s"End of log messages of test that failed with ${cause.getMessage}")
    capturingAppender.clear()
  }
}
