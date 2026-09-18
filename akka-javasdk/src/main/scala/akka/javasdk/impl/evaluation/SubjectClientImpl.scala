/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.util.concurrent.CompletionException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.NotUsed
import akka.annotation.InternalApi
import akka.javasdk.evaluation.Interaction
import akka.javasdk.evaluation.SubjectClient
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.ledger.LedgerClientImpl
import akka.runtime.sdk.spi.SpiLedger
import akka.runtime.sdk.spi.{ LedgerClient => SpiLedgerClient }
import akka.stream.scaladsl.Source

/**
 * INTERNAL API
 *
 * The public SDK [[SubjectClient]] backed by the runtime's SPI [[SpiLedgerClient]]. Pages through `getFlowInteractions`
 * internally, oldest first, so callers see one continuous stream or list regardless of how many pages that takes.
 */
@InternalApi
private[javasdk] final class SubjectClientImpl(spiLedgerClient: SpiLedgerClient, sdkExecutionContext: ExecutionContext)
    extends SubjectClient {

  private implicit val ec: ExecutionContext = sdkExecutionContext

  // internal paging size; invisible to the caller, who sees one continuous stream or bounded list
  private val PageSize = 100

  override def flowInteractions(flowId: String): akka.stream.javadsl.Source[Interaction, NotUsed] =
    Source
      .unfoldAsync(1L) { fromSeqNr =>
        spiLedgerClient.getFlowInteractions(flowId, fromSeqNr, Set.empty, PageSize).map {
          case rows if rows.isEmpty => None
          case rows                 => Some((rows.last.seqNr + 1, rows))
        }
      }
      .mapConcat(rows => rows)
      .map(row => toInteraction(row))
      .asJava

  override def flowInteractions(flowId: String, limit: Int): java.util.List[Interaction] = {
    val future: Future[java.util.List[Interaction]] =
      spiLedgerClient.getFlowInteractions(flowId, 1L, Set.empty, limit).map(_.map(row => toInteraction(row)).asJava)
    try future.asJava.toCompletableFuture.join()
    catch {
      case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
    }
  }

  private def toInteraction(row: SpiLedger.FlowInteractionRow): Interaction =
    new InteractionRecordAdapter(LedgerClientImpl.toInteractionRecord(row.interaction))
}
