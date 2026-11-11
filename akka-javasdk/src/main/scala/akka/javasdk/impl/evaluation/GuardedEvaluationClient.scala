/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Future

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiEvaluationClient
import akka.runtime.sdk.spi.SpiEvaluator
import com.typesafe.config.Config
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API
 *
 * The runtime's [[SpiEvaluationClient]] with the `enabled` setting of the evaluators applied: a start of an evaluator
 * that is disabled in `akka.javasdk.evaluation.evaluators` fails with an `IllegalStateException`, the same way as a
 * start while evaluators are disabled altogether.
 */
@InternalApi
private[javasdk] final class GuardedEvaluationClient(delegate: SpiEvaluationClient, config: Config)
    extends SpiEvaluationClient {

  override def startEvaluation(
      evaluatorComponentId: String,
      subject: SpiEvaluator.Subject,
      attributes: Map[String, String],
      telemetryContext: OtelContext): Future[String] =
    if (!EvaluatorSettings.enabled(config, evaluatorComponentId))
      Future.failed(new IllegalStateException(s"Evaluator [$evaluatorComponentId] is disabled"))
    else delegate.startEvaluation(evaluatorComponentId, subject, attributes, telemetryContext)
}
