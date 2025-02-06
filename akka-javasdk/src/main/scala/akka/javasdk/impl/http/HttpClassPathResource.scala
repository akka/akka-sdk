/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.NotUsed
import akka.http.javadsl.model.ContentTypes
import akka.http.javadsl.model.HttpEntities
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.MediaTypes
import akka.http.javadsl.model.ResponseEntity
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.HttpCharsets
import akka.stream.javadsl.StreamConverters

object HttpClassPathResource {

  private val suffixToMimeType = Map(
    "html" -> ContentTypes.TEXT_HTML_UTF8,
    "txt" -> ContentTypes.TEXT_PLAIN_UTF8,
    "css" -> ContentTypes.create(MediaTypes.TEXT_CSS, HttpCharsets.`UTF-8`),
    "js" -> ContentTypes.create(MediaTypes.APPLICATION_JAVASCRIPT, HttpCharsets.`UTF-8`),
    "png" -> ContentTypes.create(MediaTypes.IMAGE_PNG),
    "svg" -> ContentTypes.create(MediaTypes.IMAGE_SVG_XML),
    "jpg" -> ContentTypes.create(MediaTypes.IMAGE_JPEG),
    "ico" -> ContentTypes.create(MediaTypes.IMAGE_X_ICON),
    "pdf" -> ContentTypes.create(MediaTypes.APPLICATION_PDF))

  def fromStaticPath(absolutePath: String): HttpResponse = {
    require(absolutePath.startsWith("/"), "Illegal path, must be absolute")

    val url = getClass.getResource(absolutePath)
    if (url == null) {
      HttpResponse.create().withStatus(StatusCodes.NOT_FOUND)
    } else {
      val idx = absolutePath.lastIndexOf('.')

      url.openConnection()
      val contentType =
        if (idx == -1 || idx == absolutePath.length) ContentTypes.APPLICATION_OCTET_STREAM
        else {
          val suffix = absolutePath.substring(idx + 1)
          suffixToMimeType.getOrElse(suffix, ContentTypes.APPLICATION_OCTET_STREAM)
        }

      HttpResponse
        .create()
        .withEntity(
          HttpEntities.create(
            contentType,
            StreamConverters
              .fromInputStream(() => getClass.getResourceAsStream(absolutePath))
              .mapMaterializedValue(_ => NotUsed)): ResponseEntity)
    }
  }

}
