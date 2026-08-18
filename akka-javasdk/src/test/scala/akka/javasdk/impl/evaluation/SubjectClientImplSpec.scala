/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import akka.actor.ActorSystem
import akka.runtime.sdk.spi.SpiLedger
import akka.runtime.sdk.spi.{ LedgerClient => SpiLedgerClient }
import akka.stream.scaladsl.Sink
import akka.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SubjectClientImplSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("SubjectClientImplSpec")

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def interaction(interactionId: String): SpiLedger.InteractionRecord =
    new SpiLedger.InteractionRecord(
      interactionId = interactionId,
      sessionId = "flow-1",
      agentComponentId = "worker-agent",
      flowId = Some("flow-1"),
      metadata = new SpiLedger.InteractionMetadata(
        new SpiLedger.ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024),
        Map.empty,
        Instant.EPOCH,
        Instant.EPOCH,
        SpiLedger.FinishReason.Stop),
      systemMessage = "",
      userMessage = Seq.empty,
      modelResponses = Seq(new SpiLedger.ModelResponse("m", interactionId, 1, 1, "", Seq.empty)),
      toolCallResponses = Seq.empty,
      taskContext = None,
      failure = None,
      timestamp = Instant.EPOCH)

  /** A fake SPI ledger client whose `getFlowInteractions` serves fixed pages, one per call, keyed by fromSeqNr. */
  private class PagedLedgerClient(pages: Map[Long, Seq[SpiLedger.FlowInteractionRow]]) extends SpiLedgerClient {
    var calls: Seq[Long] = Seq.empty

    override def getInteraction(interactionId: String): Future[SpiLedger.InteractionRecord] = ???
    override def getEvaluation(evaluationId: String): Future[SpiLedger.EvaluationRecord] = ???
    override def getClassification(classificationId: String): Future[SpiLedger.ClassificationRecord] = ???

    override def getFlowInteractions(
        flowId: String,
        fromSeqNr: Long,
        componentIds: Set[String],
        limit: Int): Future[Seq[SpiLedger.FlowInteractionRow]] = {
      calls :+= fromSeqNr
      Future.successful(pages.getOrElse(fromSeqNr, Seq.empty))
    }
  }

  private def row(interactionId: String, seqNr: Long) =
    new SpiLedger.FlowInteractionRow(interaction(interactionId), seqNr)

  "SubjectClientImpl" should {

    "stream every interaction across multiple pages, oldest first" in assertAllStagesStopped {
      val spi = new PagedLedgerClient(Map(1L -> Seq(row("i-1", 1L), row("i-2", 2L)), 3L -> Seq(row("i-3", 3L))))
      val client = new SubjectClientImpl(spi, ExecutionContext.parasitic)

      val result =
        client.flowInteractions("flow-1").asScala.runWith(Sink.seq)(akka.stream.SystemMaterializer(system).materializer)

      result.futureValue.map(_.interactionId()) shouldBe Seq("i-1", "i-2", "i-3")
      spi.calls shouldBe Seq(1L, 3L, 4L)
    }

    "read a bounded list, blocking, without paging past the limit" in {
      val spi = new PagedLedgerClient(Map(1L -> Seq(row("i-1", 1L), row("i-2", 2L))))
      val client = new SubjectClientImpl(spi, ExecutionContext.parasitic)

      val result = client.flowInteractions("flow-1", 2)

      result.asScala.map(_.interactionId()).toSeq shouldBe Seq("i-1", "i-2")
      spi.calls shouldBe Seq(1L)
    }

    "return an empty stream for a flow with no interactions" in assertAllStagesStopped {
      val spi = new PagedLedgerClient(Map.empty)
      val client = new SubjectClientImpl(spi, ExecutionContext.parasitic)

      val result =
        client.flowInteractions("flow-1").asScala.runWith(Sink.seq)(akka.stream.SystemMaterializer(system).materializer)

      result.futureValue shouldBe empty
    }
  }
}
