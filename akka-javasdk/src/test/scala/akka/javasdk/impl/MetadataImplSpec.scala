/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Instant

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
    "support getting the subject JWT claim" in {
      metadata("_kalix-jwt-claim-sub" -> "some-subject").jwtClaims.subject().toScala.value shouldBe "some-subject"
    }

    "support getting the expiration JWT claim" in {
      metadata("_kalix-jwt-claim-exp" -> "12345").jwtClaims.expirationTime().toScala.value shouldBe Instant
        .ofEpochSecond(12345)
    }

    "support parsing object JWT claims" in {
      val jsonNode =
        metadata("_kalix-jwt-claim-my-object" -> """{"foo":"bar"}""").jwtClaims().getObject("my-object").toScala.value
      jsonNode.get("foo").textValue() shouldBe "bar"
    }

    "support parsing string list JWT claims" in {
      val list = metadata("_kalix-jwt-claim-my-string-list" -> """["foo","bar"]""")
        .jwtClaims()
        .getStringList("my-string-list")
        .toScala
        .value
      (list.asScala should contain).theSameElementsInOrderAs(List("foo", "bar"))
    }

    "support parsing int list JWT claims" in {
      val list = metadata("_kalix-jwt-claim-my-int-list" -> """[3,4]""")
        .jwtClaims()
        .getIntegerList("my-int-list")
        .toScala
        .value
      (list.asScala should contain).theSameElementsInOrderAs(List(3, 4))
    }

    "ignore claims that are not the right type" in {
      val meta = metadata("_kalix-jwt-claim-foo" -> "bar")
      meta.jwtClaims().getBoolean("foo").toScala shouldBe None
      meta.jwtClaims().getInteger("foo").toScala shouldBe None
      meta.jwtClaims().getLong("foo").toScala shouldBe None
      meta.jwtClaims().getDouble("foo").toScala shouldBe None
      meta.jwtClaims().getNumericDate("foo").toScala shouldBe None
      meta.jwtClaims().getObject("foo").toScala shouldBe None
      meta.jwtClaims().getBooleanList("foo").toScala shouldBe None
      meta.jwtClaims().getIntegerList("foo").toScala shouldBe None
      meta.jwtClaims().getLongList("foo").toScala shouldBe None
      meta.jwtClaims().getDoubleList("foo").toScala shouldBe None
      meta.jwtClaims().getNumericDateList("foo").toScala shouldBe None
      meta.jwtClaims().getObjectList("foo").toScala shouldBe None
      meta.jwtClaims().getStringList("foo").toScala shouldBe None
    }

    "ignore claims that don't exist" in {
      val meta = metadata("_kalix-jwt-claim-x" -> "bar")
      meta.jwtClaims().getString("foo").toScala shouldBe None
      meta.jwtClaims().getBoolean("foo").toScala shouldBe None
      meta.jwtClaims().getInteger("foo").toScala shouldBe None
      meta.jwtClaims().getLong("foo").toScala shouldBe None
      meta.jwtClaims().getDouble("foo").toScala shouldBe None
      meta.jwtClaims().getNumericDate("foo").toScala shouldBe None
      meta.jwtClaims().getObject("foo").toScala shouldBe None
      meta.jwtClaims().getBooleanList("foo").toScala shouldBe None
      meta.jwtClaims().getIntegerList("foo").toScala shouldBe None
      meta.jwtClaims().getLongList("foo").toScala shouldBe None
      meta.jwtClaims().getDoubleList("foo").toScala shouldBe None
      meta.jwtClaims().getNumericDateList("foo").toScala shouldBe None
      meta.jwtClaims().getObjectList("foo").toScala shouldBe None
      meta.jwtClaims().getStringList("foo").toScala shouldBe None
    }

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
