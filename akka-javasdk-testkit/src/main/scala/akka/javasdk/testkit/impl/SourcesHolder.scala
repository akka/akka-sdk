/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.actor.Actor
import akka.javasdk.testkit.TestKit
import akka.javasdk.testkit.impl.EventingTestKitImpl.RunningSourceProbe
import akka.javasdk.testkit.impl.SourcesHolder.AddSource
import akka.javasdk.testkit.impl.SourcesHolder.Publish
import akka.javasdk.{ Metadata => SdkMetadata }
import com.google.protobuf.ByteString
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer

object SourcesHolder {

  case class AddSource(runningSourceProbe: RunningSourceProbe)
  case class Publish(message: ByteString, metadata: SdkMetadata)
}

class SourcesHolder extends Actor {

  private val log = LoggerFactory.getLogger(classOf[TestKit])

  private val sources: ArrayBuffer[RunningSourceProbe] = ArrayBuffer.empty
  private val publishedMessages: ArrayBuffer[PublishedMessage] = ArrayBuffer.empty

  private case class PublishedMessage(message: ByteString, metadata: SdkMetadata)

  override def receive: Receive = {
    case AddSource(runningSourceProbe) =>
      if (publishedMessages.nonEmpty) {
        log.debug(
          s"Emitting ${publishedMessages.size} messages to new source ${runningSourceProbe.serviceName}/${runningSourceProbe.source.source}")
        publishedMessages.foreach { msg =>
          runningSourceProbe.emit(msg.message, msg.metadata)
        }
      }
      sources.addOne(runningSourceProbe)
      log.debug(s"Source added ${runningSourceProbe.serviceName}/${runningSourceProbe.source.source}")
      sender() ! "ok"
    case Publish(message, metadata) =>
      sources.foreach { source =>
        log.debug(s"Emitting message to source ${source.serviceName}/${source.source.source}")
        source.emit(message, metadata)
      }
      publishedMessages.addOne(PublishedMessage(message, metadata))
      sender() ! "ok"
  }
}
