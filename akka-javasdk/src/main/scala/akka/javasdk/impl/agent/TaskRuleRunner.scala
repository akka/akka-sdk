/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.task.TaskRule
import akka.javasdk.agent.task.TaskState
import akka.javasdk.impl.serialization.Serializer
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object TaskRuleRunner {
  sealed trait RuleOutcome
  object RuleOutcome {
    case object Accepted extends RuleOutcome
    final case class Rejected(ruleClassName: String, reason: String) extends RuleOutcome
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] class TaskRuleRunner(system: ActorSystem, serializer: Serializer) {
  import TaskRuleRunner._

  private val dynamicAccess = system.asInstanceOf[ExtendedActorSystem].dynamicAccess

  private val log = LoggerFactory.getLogger(classOf[TaskRuleRunner])

  def evaluate(taskState: TaskState, resultJson: String): RuleOutcome = {
    val ruleClassNames = taskState.ruleClassNames().asScala.toSeq
    if (ruleClassNames.isEmpty) {
      RuleOutcome.Accepted
    } else {
      val resultTypeName = Option(taskState.resultTypeName()).getOrElse(classOf[String].getName)
      val result = deserializeResult(resultJson, resultTypeName)
      runRules(ruleClassNames, result)
    }
  }

  private def deserializeResult(resultJson: String, resultTypeName: String): Any = {
    if (resultTypeName == classOf[String].getName) {
      resultJson
    } else {
      serializer.json.fromJsonString(resultJson, Class.forName(resultTypeName))
    }
  }

  private def runRules(ruleClassNames: Seq[String], result: Any): RuleOutcome = {
    ruleClassNames.iterator
      .map { className =>
        dynamicAccess.createInstanceFor[TaskRule[Any]](className, Nil) match {
          case Success(rule) =>
            val ruleResult = rule.onComplete(result)
            (className, Some(ruleResult))
          case Failure(ex) =>
            log.error("Failed to instantiate task rule [{}]: {}", className, ex.getMessage)
            return RuleOutcome.Rejected(className, s"Failed to instantiate task rule: ${ex.getMessage}")
        }
      }
      .collectFirst { case (className, Some(rejected: TaskRule.Result.Rejected)) =>
        log.debug("Task rule [{}] rejected: {}", className, rejected.reason())
        RuleOutcome.Rejected(className, rejected.reason())
      }
      .getOrElse(RuleOutcome.Accepted)
  }
}
