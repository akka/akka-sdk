/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import java.io.IOException
import java.lang
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import akka.javasdk.JsonMigration
import akka.javasdk.JsonSupport
import akka.javasdk.annotations.Migration
import akka.javasdk.annotations.TypeName
import akka.javasdk.impl.NullSerializationException
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.core.JsonProcessingException

object JsonSerializer {
  val JsonContentTypePrefix: String = "json.akka.io/"
  private val KalixJsonContentTypePrefix: String = "json.kalix.io/"

  final case class TypeHint(currenTypeHintWithVersion: String, allTypeHints: List[String])

}

class JsonSerializer {
  import JsonSerializer._

  private val typeHints: ConcurrentMap[Class[_], TypeHint] = new ConcurrentHashMap()
  val reversedTypeHints: ConcurrentMap[String, Class[_]] = new ConcurrentHashMap()

  override def toString: String = s"JsonSerializer: ${typeHints.keySet().size()} registered types"

  private val objectMapper = JsonSupport.getObjectMapper

  def toBytes(value: Any): BytesPayload = {
    if (value == null) throw NullSerializationException
    val typeHint = lookupTypeHintWithVersion(value)
    val byteArray = objectMapper.writerFor(value.getClass).writeValueAsBytes(value)
    new BytesPayload(bytes = ByteString.fromArrayUnsafe(byteArray), contentType = JsonContentTypePrefix + typeHint)
  }

  def fromBytes[T](expectedType: Class[T], bytesPayload: BytesPayload): T = {
    validateIsJson(bytesPayload)

    try {
      val migrationAnnotation = expectedType.getAnnotation(classOf[Migration])
      if (migrationAnnotation != null) {
        val migration = migrationAnnotation
          .value()
          .getConstructor()
          .newInstance()
        val fromVersion = parseVersion(bytesPayload.contentType)
        val currentVersion = migration.currentVersion()
        val supportedForwardVersion = migration.supportedForwardVersion
        if (fromVersion < currentVersion) {
          migrate(expectedType, bytesPayload.bytes, fromVersion, migration);
        } else if (fromVersion == currentVersion) {
          parseBytes(expectedType, bytesPayload.bytes)
        } else if (fromVersion <= supportedForwardVersion) {
          migrate(expectedType, bytesPayload.bytes, fromVersion, migration)
        } else {
          throw new IllegalStateException(
            s"Migration version [$supportedForwardVersion] is " +
            "behind version [$fromVersion] of deserialized type [${expectedType.getName}]")
        }
      } else {
        parseBytes(expectedType, bytesPayload.bytes)
      }
    } catch {
      case e: JsonProcessingException =>
        throw jsonProcessingException(expectedType, bytesPayload.contentType, e)
      case e @ (_: IOException | _: NoSuchMethodException | _: InstantiationException | _: IllegalAccessException |
          _: InvocationTargetException) =>
        throw genericDecodeException(expectedType, bytesPayload.contentType, e)
    }
  }

  /**
   * Parse the bytes to object of type corresponding to the type name in the `bytesPayload.contentType`. Requires that
   * the types are known by first registering them by calling `contentTypeFor` or `toBytes`.
   */
  def fromBytes(bytesPayload: BytesPayload): AnyRef = {
    validateIsJson(bytesPayload)

    val typeName = removeVersion(stripJsonContentTypePrefix(bytesPayload.contentType))
    val typeClass = reversedTypeHints.get(typeName).asInstanceOf[Class[AnyRef]]
    if (typeClass eq null)
      throw new IllegalStateException(
        s"Cannot decode [${bytesPayload.contentType}] message type. Class mapping not found.")
    else
      fromBytes(typeClass, bytesPayload)
  }

  private def parseVersion(contentType: String): Int = {
    val versionSeparatorIndex = contentType.lastIndexOf('#')
    if (versionSeparatorIndex > 0) {
      contentType.substring(versionSeparatorIndex + 1).toInt
    } else
      0
  }

  private def migrate[T](valueClass: Class[T], bytes: ByteString, fromVersion: Int, jsonMigration: JsonMigration): T = {
    val jsonNode = objectMapper.readTree(bytes.toArrayUnsafe())
    val newJsonNode = jsonMigration.transform(fromVersion, jsonNode)
    objectMapper.treeToValue(newJsonNode, valueClass)
  }

  private def parseBytes[T](valueClass: Class[T], bytes: ByteString): T = {
    objectMapper.readValue(bytes.toArrayUnsafe(), valueClass)
  }

  private def jsonProcessingException[T](valueClass: Class[T], contentType: String, e: JsonProcessingException) =
    new IllegalArgumentException(
      s"JSON with contentType [$contentType] could not be decoded into a " +
      s"[${valueClass.getName}]. Make sure that changes are backwards compatible or apply a @Migration " +
      "mechanism (https://doc.akka.io/java/serialization.html#_schema_evolution).",
      e)

  private def genericDecodeException[T](valueClass: Class[T], contentType: String, e: Throwable) =
    new IllegalArgumentException(
      s"JSON with contentType [$contentType] could not be decoded " +
      s"into a [${valueClass.getName}]",
      e)

  private def validateIsJson(bytesPayload: BytesPayload): Unit = {
    if (!isJson(bytesPayload))
      throw new IllegalArgumentException(
        s"BytesPayload with contentTYpe [${bytesPayload.contentType}] " +
        s"cannot be decoded as JSON, must start with [$JsonContentTypePrefix]")
  }

  private def isJson(bytesPayload: BytesPayload) =
    isJsonTypeUrl(bytesPayload.contentType)

  private def isJsonTypeUrl(contentType: String): Boolean =
    // check both new and old typeurl for compatibility, in case there are services with old type url stored in database
    contentType.startsWith(JsonContentTypePrefix) || contentType.startsWith(KalixJsonContentTypePrefix)

// FIXME could be used by some ReflectiveRouters but not yet
//  private def replaceLegacyJsonPrefix(typeUrl: String): String =
//    if (typeUrl.startsWith(KalixJsonContentTypePrefix))
//      JsonContentTypePrefix + typeUrl.stripPrefix(KalixJsonContentTypePrefix)
//    else typeUrl

  private def stripJsonContentTypePrefix(contentType: String): String =
    contentType.stripPrefix(JsonContentTypePrefix).stripPrefix(KalixJsonContentTypePrefix)

  private def lookupTypeHintWithVersion(value: Any): String =
    lookupTypeHint(value.getClass).currenTypeHintWithVersion

  private[akka] def lookupTypeHint(clz: Class[_]): TypeHint = {
    typeHints.computeIfAbsent(clz, computeTypeHint)
  }

  private[akka] def registerTypeHints(clz: Class[_]): Unit = {
    lookupTypeHint(clz)
    if (clz.getAnnotation(classOf[JsonSubTypes]) != null) {
      //registering all subtypes
      clz
        .getAnnotation(classOf[JsonSubTypes])
        .value()
        .map(_.value())
        .foreach(lookupTypeHint)
    }
  }

  private def computeTypeHint(clz: Class[_]): TypeHint = {
    if (clz.getName.contains("java.lang")) {
      val typeHint = if (clz.isAssignableFrom(classOf[String])) {
        TypeHint("string", List("string", "java.lang.String"))
      } else if (clz.isAssignableFrom(classOf[lang.Integer])) {
        TypeHint("int", List("int", "java.lang.Integer"))
      } else if (clz.isAssignableFrom(classOf[lang.Long])) {
        TypeHint("long", List("long", "java.lang.Long"))
      } else if (clz.isAssignableFrom(classOf[lang.Boolean])) {
        TypeHint("boolean", List("boolean", "java.lang.Boolean"))
      } else if (clz.isAssignableFrom(classOf[lang.Double])) {
        TypeHint("double", List("double", "java.lang.Double"))
      } else if (clz.isAssignableFrom(classOf[lang.Float])) {
        TypeHint("float", List("float", "java.lang.Float"))
      } else if (clz.isAssignableFrom(classOf[lang.Character])) {
        TypeHint("char", List("char", "java.lang.Character"))
      } else if (clz.isAssignableFrom(classOf[lang.Byte])) {
        TypeHint("byte", List("byte", "java.lang.Byte"))
      } else if (clz.isAssignableFrom(classOf[lang.Short])) {
        TypeHint("short", List("short", "java.lang.Short"))
      } else {
        TypeHint(clz.getName, List(clz.getName))
      }
      typeHint.allTypeHints.foreach(className => addToReversedCache(clz, className))
      typeHint
    } else {
      val typeName = Option(clz.getAnnotation(classOf[TypeName]))
        .collect { case ann if ann.value().trim.nonEmpty => ann.value() }
        .getOrElse(clz.getName)

      val (version, supportedClassNames) = getVersionAndSupportedClassNames(clz)
      val typeNameWithVersion = typeName + (if (version == 0) "" else "#" + version)

      addToReversedCache(clz, typeName)
      supportedClassNames.foreach(className => addToReversedCache(clz, className))

      TypeHint(typeNameWithVersion, typeName :: supportedClassNames)
    }
  }

  private def addToReversedCache(clz: Class[_], typeName: String) = {
    reversedTypeHints.compute(
      typeName,
      (_, currentValue) => {
        if (currentValue eq null) {
          clz
        } else if (currentValue == clz) {
          currentValue
        } else {
          throw new IllegalStateException(
            "Collision with existing existing mapping " + currentValue + " -> " + typeName + ". The same type name can't be used for other class " + clz)
        }
      })
  }

  private def getVersionAndSupportedClassNames(clz: Class[_]): (Int, List[String]) = {
    import scala.jdk.CollectionConverters._
    Option(clz.getAnnotation(classOf[Migration]))
      .map(_.value())
      .map(migrationClass => migrationClass.getConstructor().newInstance())
      .map(migration =>
        (migration.currentVersion(), migration.supportedClassNames().asScala.toList)) //TODO what about TypeName
      .getOrElse((0, List.empty))
  }

  def contentTypeFor(clz: Class[_]): String =
    JsonContentTypePrefix + lookupTypeHint(clz).currenTypeHintWithVersion

  def contentTypesFor(clz: Class[_]): List[String] =
    lookupTypeHint(clz).allTypeHints.map(JsonContentTypePrefix + _)

  private[akka] def removeVersion(typeName: String) = {
    typeName.split("#").head
  }
}
