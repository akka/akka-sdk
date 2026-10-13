/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.time.Instant

import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.query.TimestampOffset
import akka.persistence.query.typed.EventEnvelope
import akka.projection.AllowSeqNrGapsMetadata
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class StreamOutgoingMessagesImplSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {

  private def envelope(
      pid: String,
      seqNr: Long,
      source: String,
      payload: Boolean = true,
      filtered: Boolean = false): EventEnvelope[AnyRef] =
    EventEnvelope[AnyRef](
      TimestampOffset(Instant.EPOCH.plusMillis(seqNr), Map(pid -> seqNr)),
      pid,
      seqNr,
      if (payload) s"$pid-$seqNr" else null,
      seqNr,
      "entity",
      0,
      filtered,
      source)

  private def query(pid: String, seqNr: Long) = envelope(pid, seqNr, "")
  private def pubSub(pid: String, seqNr: Long) = envelope(pid, seqNr, "PS")
  private def backtracking(pid: String, seqNr: Long) = envelope(pid, seqNr, "BT", payload = false)
  private def snapshot(pid: String, seqNr: Long) = envelope(pid, seqNr, "SN")
  private def filteredEvent(pid: String, seqNr: Long) = envelope(pid, seqNr, "", payload = false, filtered = true)
  private def gapsAllowed(pid: String, seqNr: Long) = query(pid, seqNr).withMetadata(AllowSeqNrGapsMetadata)

  private def run(envelopes: EventEnvelope[AnyRef]*): Seq[(String, Long, String)] =
    Source(envelopes.toList)
      .via(StreamOutgoingMessagesImpl.offsetStoreFilter("test-stream"))
      .runWith(Sink.seq)
      .futureValue
      .map(env => (env.persistenceId, env.sequenceNr, env.source))

  "The stream outgoing offset store filter" must {

    "pass on a pub-sub event once when the query copy follows" in {
      run(pubSub("a", 1), query("a", 1)) shouldBe Seq(("a", 1, "PS"))
    }

    "pass on a query event once when a late pub-sub copy follows" in {
      run(query("a", 1), pubSub("a", 1)) shouldBe Seq(("a", 1, ""))
    }

    "drop a pub-sub event that arrives before the event before it" in {
      run(pubSub("a", 2), query("a", 1), query("a", 2)) shouldBe Seq(("a", 1, ""), ("a", 2, ""))
    }

    "drop a query event that arrives before the event before it" in {
      run(query("a", 2), query("a", 1), query("a", 2)) shouldBe Seq(("a", 1, ""), ("a", 2, ""))
    }

    "drop a query event after a gap until the gap is filled" in {
      run(query("a", 1), query("a", 3), query("a", 2), query("a", 3)) shouldBe
      Seq(("a", 1, ""), ("a", 2, ""), ("a", 3, ""))
    }

    "drop an event below the last accepted one" in {
      val events = (1L to 8L).map(query("a", _)) :+ query("a", 7)
      run(events: _*) shouldBe (1L to 8L).map(("a", _, ""))
    }

    "count a filtered event in the sequence" in {
      run(query("a", 1), filteredEvent("a", 2), query("a", 3)) shouldBe
      Seq(("a", 1, ""), ("a", 2, ""), ("a", 3, ""))
    }

    "not count a backtracking envelope without a payload in the sequence" in {
      run(query("a", 1), backtracking("a", 2), query("a", 2)) shouldBe Seq(("a", 1, ""), ("a", 2, ""))
    }

    "accept a snapshot above the next number" in {
      run(snapshot("a", 5), query("a", 6), query("a", 5)) shouldBe Seq(("a", 5, "SN"), ("a", 6, ""))
    }

    "accept a gap in events with AllowSeqNrGapsMetadata" in {
      run(gapsAllowed("a", 1), gapsAllowed("a", 3), gapsAllowed("a", 2)) shouldBe Seq(("a", 1, ""), ("a", 3, ""))
    }

    "track each persistence id on its own" in {
      run(
        query("a", 1),
        query("b", 1),
        pubSub("b", 3),
        pubSub("a", 2),
        query("b", 2),
        query("a", 2),
        query("b", 3)) shouldBe
      Seq(("a", 1, ""), ("b", 1, ""), ("a", 2, "PS"), ("b", 2, ""), ("b", 3, ""))
    }

    "log a warning for a backtracking envelope at the next number" in {
      LoggingTestKit.warn("did not receive persistence id [a] sequence number [2] with a payload").expect {
        run(query("a", 1), backtracking("a", 2)) shouldBe Seq(("a", 1, ""))
      }
    }

    "log a warning for a gap from the query" in {
      LoggingTestKit
        .warn("dropped persistence id [a] sequence number [3] from the database query, because sequence number [2]")
        .expect {
          run(query("a", 1), query("a", 3)) shouldBe Seq(("a", 1, ""))
        }
    }

    "not log a warning for a pub-sub gap or a copy" in {
      LoggingTestKit.warn("Stream [test-stream]").withOccurrences(0).expect {
        run(
          query("a", 1),
          pubSub("a", 3),
          query("a", 2),
          query("a", 3),
          pubSub("a", 4),
          query("a", 4),
          backtracking("a", 1)) shouldBe
        Seq(("a", 1, ""), ("a", 2, ""), ("a", 3, ""), ("a", 4, "PS"))
      }
    }
  }
}
