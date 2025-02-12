/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.NotUsed
import akka.http.javadsl.model.ContentTypes
import akka.http.javadsl.model.DateTime
import akka.http.javadsl.model.HttpEntities
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.MediaTypes
import akka.http.javadsl.model.ResponseEntity
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.model.headers.LastModified
import akka.http.scaladsl.model.HttpCharsets
import akka.stream.javadsl.StreamConverters

object HttpClassPathResource {

  private val PredefinedStaticResourcesPath = "/static-resources/"

  private val suffixToMimeType = Map(
    "html" -> ContentTypes.TEXT_HTML_UTF8,
    "txt" -> ContentTypes.TEXT_PLAIN_UTF8,
    "css" -> ContentTypes.create(MediaTypes.TEXT_CSS, HttpCharsets.`UTF-8`),
    "js" -> ContentTypes.create(MediaTypes.APPLICATION_JAVASCRIPT, HttpCharsets.`UTF-8`),
    "png" -> ContentTypes.create(MediaTypes.IMAGE_PNG),
    "svg" -> ContentTypes.create(MediaTypes.IMAGE_SVG_XML),
    "jpg" -> ContentTypes.create(MediaTypes.IMAGE_JPEG),
    "gif" -> ContentTypes.create(MediaTypes.IMAGE_GIF),
    "ico" -> ContentTypes.create(MediaTypes.IMAGE_X_ICON),
    "pdf" -> ContentTypes.create(MediaTypes.APPLICATION_PDF))

  def fromStaticPath(relativePath: String): HttpResponse = {
    // not http response since it would be a programmer error
    if (relativePath.startsWith("/"))
      throw new IllegalArgumentException(s"Illegal path [$relativePath], is relative, must not start with '/'")

    if (relativePath.contains("..")) {
      HttpResponse
        .create()
        .withStatus(StatusCodes.FORBIDDEN)
        .withEntity("Relative paths not allowed")
    } else {

      val actualPath = PredefinedStaticResourcesPath + relativePath
      val url = getClass.getResource(PredefinedStaticResourcesPath + relativePath)
      if (url == null) {
        HttpResponse.create().withStatus(StatusCodes.NOT_FOUND)
      } else {
        val idx = actualPath.lastIndexOf('.')

        val urlConnection = url.openConnection() // Note: not actually opening anything, nothing to close
        val contentLength = urlConnection.getContentLengthLong

        val contentType =
          if (idx == -1 || idx == actualPath.length) ContentTypes.APPLICATION_OCTET_STREAM
          else {
            val suffix = actualPath.substring(idx + 1)
            suffixToMimeType.getOrElse(suffix, ContentTypes.APPLICATION_OCTET_STREAM)
          }

        HttpResponse
          .create()
          .addHeader(LastModified.create(DateTime.create(urlConnection.getLastModified)))
          .withEntity(
            HttpEntities.create(
              contentType,
              contentLength,
              StreamConverters
                .fromInputStream(() => getClass.getResourceAsStream(actualPath))
                .mapMaterializedValue(_ => NotUsed)): ResponseEntity)
      }
    }
  }

}
