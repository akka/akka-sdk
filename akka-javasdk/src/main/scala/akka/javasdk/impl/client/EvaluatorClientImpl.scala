/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util
import java.util.concurrent.CompletionStage

import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.annotation.InternalApi
import akka.javasdk.client.EvaluatorClient
import akka.javasdk.evaluation.Subject
import akka.javasdk.impl.evaluation.EvaluationConversions
import akka.runtime.sdk.spi.SpiEvaluationClient

/**
 * INTERNAL API
 *
 * The public [[EvaluatorClient]] for one evaluator, backed by the runtime's [[SpiEvaluationClient]].
 */
@InternalApi
private[javasdk] final class EvaluatorClientImpl(evaluatorComponentId: String, evaluationClient: SpiEvaluationClient)
    extends EvaluatorClient {

  override def startEvaluationAsync(subject: Subject, attributes: util.Map[String, String]): CompletionStage[String] = {
    if (subject eq null) throw new NullPointerException("Subject is null")
    if (attributes eq null) throw new NullPointerException("Attributes are null")
    evaluationClient
      .startEvaluation(evaluatorComponentId, EvaluationConversions.toSpiSubject(subject), attributes.asScala.toMap)
      .asJava
  }
}
