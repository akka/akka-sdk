/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.http.javadsl.model.StatusCodes
import akka.javasdk.Metadata
import kalix.protocol.component.MetadataEntry
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MetadataImplSpec extends AnyWordSpec with Matchers with OptionValues {

  "MetadataImpl" should {

    "support setting a HTTP status code" in {
      val md = Metadata.EMPTY.withStatusCode(StatusCodes.CREATED)
      md.get("_kalix-http-code").toScala.value shouldBe "201"

      val mdRedirect = md.withStatusCode(StatusCodes.MOVED_PERMANENTLY)
      mdRedirect.get("_kalix-http-code").toScala.value shouldBe "301"
    }

    "support creating with CloudEvents prefixed with ce_" in {
      val md = metadata("ce_id" -> "id", "ce_source" -> "source", "ce_specversion" -> "1.0", "ce_type" -> "foo")
      md.isCloudEvent shouldBe true
      val ce = md.asCloudEvent()
      ce.id() shouldBe "id"
      ce.source().toString shouldBe "source"
      ce.specversion() shouldBe "1.0"
      ce.`type`() shouldBe "foo"
    }

    "metadata should be mergeable" in {
      val md1 = metadata("foo" -> "bar", "foobar" -> "raboof")
      val md2 = metadata("baz" -> "qux", "foobar" -> "foobar")
      val merged = md1.merge(md2)
      merged.get("foo").toScala.value shouldBe "bar"
      merged.get("baz").toScala.value shouldBe "qux"

      val expectedEntries = "foobar" :: "raboof" :: Nil
      merged.getAll("foobar").asScala should contain theSameElementsAs expectedEntries
    }
  }

  private def metadata(entries: (String, String)*): Metadata = {
    MetadataImpl.of(entries.map { case (key, value) =>
      MetadataEntry(key, MetadataEntry.Value.StringValue(value))
    })
  }

}
