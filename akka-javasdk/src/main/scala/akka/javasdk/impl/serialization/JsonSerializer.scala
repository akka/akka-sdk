/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import java.io.IOException
import java.lang
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.CommandException
import akka.javasdk.JsonMigration
import akka.javasdk.annotations.Migration
import akka.javasdk.annotations.TypeName
import akka.javasdk.impl.AnySupport.BytesPrimitive
import akka.javasdk.impl.NullSerializationException
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer
import com.fasterxml.jackson.databind.jsontype.TypeSerializer
import com.fasterxml.jackson.databind.module.SimpleModule

/**
 * INTERNAL API
 */
@InternalApi
object JsonSerializer {
  val JsonContentTypePrefix: String = "json.akka.io/"
  private val KalixJsonContentTypePrefix: String = "json.kalix.io/"

  final case class TypeHint(currenTypeHintWithVersion: String, allTypeHints: List[String])

  def newObjectMapperWithDefaults(): ObjectMapper = {
    val mapper = new ObjectMapper

    // Date/time in ISO-8601 (rfc3339) yyyy-MM-dd'T'HH:mm:ss.SSSZ format
    // as defined by com.fasterxml.jackson.databind.util.StdDateFormat
    // For interoperability it's better to use the ISO format, i.e. WRITE_DATES_AS_TIMESTAMPS=off,
    // but WRITE_DATES_AS_TIMESTAMPS=on has better performance.
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    mapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)

    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

    // ParameterNamesModule needs the parameter to ensure that single-parameter
    // constructors are handled the same way as constructors with multiple parameters.
    // See https://github.com/FasterXML/jackson-module-parameter-names#delegating-creator
    mapper.registerModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
    mapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module())
    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())

    val module = new SimpleModule()
    module.addSerializer(classOf[Done], DoneSerializer)
    module.addDeserializer(classOf[Done], DoneDeserializer)
    mapper.registerModule(module)
    mapper.addMixIn(classOf[Throwable], classOf[ThrowableMixin])

    mapper
  }

  // internal mapper used for serialization when passing objects to the runtime
  val internalObjectMapper: ObjectMapper = newObjectMapperWithDefaults()

  object DoneSerializer extends com.fasterxml.jackson.databind.JsonSerializer[Done] {

    override def serialize(value: Done, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeStartObject()
      gen.writeEndObject()
    }

    override def serializeWithType(
        value: Done,
        gen: JsonGenerator,
        serializers: SerializerProvider,
        typeSer: TypeSerializer): Unit = {
      val typeId = typeSer.typeId(value, JsonToken.START_OBJECT)
      typeSer.writeTypePrefix(gen, typeId)
      gen.writeFieldName("value")
      gen.writeStartObject()
      gen.writeEndObject()
      typeSer.writeTypeSuffix(gen, typeId)
    }

  }

  object DoneDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer[Done] {

    override def deserialize(p: JsonParser, ctxt: DeserializationContext): Done = {
      if (p.currentToken() == JsonToken.START_OBJECT && p.nextToken() == JsonToken.END_OBJECT) {
        Done
      } else {
        throw JsonMappingException.from(ctxt, "Cannot deserialize Done class, expecting empty object '{}'")
      }
    }

    override def deserializeWithType(
        p: JsonParser,
        ctxt: DeserializationContext,
        typeDeserializer: TypeDeserializer): AnyRef =
      typeDeserializer.deserializeTypedFromObject(p, ctxt)

  }
}

/**
 * INTERNAL API
 */
@InternalApi
final class JsonSerializer(val objectMapper: ObjectMapper) {
  import JsonSerializer._

  def this() = this(JsonSerializer.internalObjectMapper)

  /* Mostly used for internal serialization (events, snapshots, messages sent through component client)
   * but in some places (consumers) it is created using the user configured object mapper if interacting
   * with the outside world (message broker consume or publish) so that users can configure for surprising
   * external formats.
   */

  private val typeHints: ConcurrentMap[Class[_], TypeHint] = new ConcurrentHashMap()
  val reversedTypeHints: ConcurrentMap[String, Class[_]] = new ConcurrentHashMap()

  override def toString: String = s"JsonSerializer: ${typeHints.keySet().size()} registered types"

  def toBytes(value: Any): BytesPayload = {
    if (value == null) throw NullSerializationException
    val typeHint = lookupTypeHintWithVersion(value)
    val byteArray = objectMapper.writerFor(value.getClass).writeValueAsBytes(value)
    new BytesPayload(bytes = ByteString.fromArrayUnsafe(byteArray), contentType = JsonContentTypePrefix + typeHint)
  }

  def toJsonString(value: Any): String = {
    if (value == null) throw NullSerializationException
    objectMapper.writerFor(value.getClass).writeValueAsString(value)
  }

  def fromBytes[T](expectedType: Type, bytesPayload: BytesPayload): T = {
    val clazz = expectedType match {
      case parameterizedType: ParameterizedType =>
        parameterizedType.getRawType.asInstanceOf[Class[T]]
      case other => other.asInstanceOf[Class[T]]
    }
    try {
      val migrationAnnotation = clazz.getAnnotation(classOf[Migration])
      if (migrationAnnotation != null) {
        val migration = migrationAnnotation
          .value()
          .getConstructor()
          .newInstance()
        val fromVersion = parseVersion(bytesPayload.contentType)
        val currentVersion = migration.currentVersion()
        val supportedForwardVersion = migration.supportedForwardVersion
        if (fromVersion < currentVersion) {
          migrate(clazz, bytesPayload.bytes, fromVersion, migration);
        } else if (fromVersion == currentVersion) {
          parseBytes(clazz, bytesPayload.bytes)
        } else if (fromVersion <= supportedForwardVersion) {
          migrate(clazz, bytesPayload.bytes, fromVersion, migration)
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
        throw jsonProcessingException(clazz, bytesPayload.contentType, e)
      case e @ (_: IOException | _: NoSuchMethodException | _: InstantiationException | _: IllegalAccessException |
          _: InvocationTargetException) =>
        throw genericDecodeException(clazz, bytesPayload.contentType, e)
    }
  }

  def fromBytes[T](expectedType: Class[T], bytesPayload: BytesPayload): T = {
    validateIsJson(bytesPayload)
    fromBytes(expectedType.asInstanceOf[Type], bytesPayload).asInstanceOf[T]
  }

  /**
   * Parse the bytes to object of type corresponding to the type name in the `bytesPayload.contentType`. Requires that
   * the types are known by first `registerTypeHints` or calling `contentTypeFor` or `toBytes`.
   */
  def fromBytes(bytesPayload: BytesPayload): AnyRef = {
    validateIsJson(bytesPayload)

    val typeName = removeVersion(stripJsonContentTypePrefix(bytesPayload.contentType))
    if (typeName == classOf[Done.type].getName || typeName == classOf[Done].getName) {
      // small optimization for Done, which does not need to be deserialized
      Done.done()
    } else {
      val typeClass = reversedTypeHints.get(typeName).asInstanceOf[Class[AnyRef]]
      if (typeClass eq null)
        throw new IllegalStateException(
          s"Cannot decode [${bytesPayload.contentType}] message type. Class mapping not found.")
      else
        fromBytes(typeClass, bytesPayload)
    }
  }

  def exceptionFromBytes(exceptionPayload: BytesPayload): CommandException = {
    val exClass = exceptionClass(exceptionPayload)
    fromBytes(exClass, exceptionPayload).asInstanceOf[CommandException]
  }

  private def exceptionClass(exceptionPayload: BytesPayload): Class[_] = {
    val className = stripJsonContentTypePrefix(exceptionPayload.contentType)
    val exClass = reversedTypeHints.get(className)
    if (exClass eq null) {
      val loadedExceptionClass = Class.forName(className)
      if (classOf[CommandException].isAssignableFrom(loadedExceptionClass)) {
        addToReversedCache(loadedExceptionClass, className)
        loadedExceptionClass
      } else {
        throw new IllegalStateException(
          "Loaded class [" + className +
          "] is not a subclass of CommandException, cannot deserialize it as a CommandException.")
      }
    } else {
      exClass
    }
  }

  def fromBytes[T, C <: util.Collection[T]](
      valueClass: Class[T],
      collectionType: Class[C],
      bytesPayload: BytesPayload): C = {
    validateIsJson(bytesPayload)

    try {
      val typeRef = objectMapper.getTypeFactory.constructCollectionType(collectionType, valueClass)
      objectMapper.readValue(bytesPayload.bytes.toArrayUnsafe(), typeRef)
    } catch {
      case e: JsonProcessingException =>
        throw jsonProcessingException(valueClass, bytesPayload.contentType, e)
      case e: IOException =>
        throw genericDecodeException(valueClass, bytesPayload.contentType, e)
    }
  }

  private def parseVersion(contentType: String) = {
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

  private def parseBytes[T](valueType: Type, bytes: ByteString): T = {
    val clazz = valueType match {
      case c: Class[_]          => c
      case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
    }
    valueType match {
      case p: ParameterizedType if classOf[java.util.Collection[_]].isAssignableFrom(clazz) =>
        objectMapper
          .readerForListOf(p.getActualTypeArguments.head.asInstanceOf[Class[_]])
          .readValue(bytes.toArrayUnsafe())
      case p: ParameterizedType if classOf[java.util.Optional[_]].isAssignableFrom(clazz) =>
        Optional
          .ofNullable(
            objectMapper.readValue(bytes.toArrayUnsafe(), p.getActualTypeArguments.head.asInstanceOf[Class[_]]))
          .asInstanceOf[T]
      case p: ParameterizedType =>
        // for other parameterized types we rely on jackson being able to handle it
        objectMapper.readValue(bytes.toArrayUnsafe(), clazz).asInstanceOf[T]
      case valueClass: Class[_] =>
        objectMapper.readValue(bytes.toArrayUnsafe(), valueClass).asInstanceOf[T]
    }
  }

  private def jsonProcessingException[T](valueClass: Class[T], contentType: String, e: JsonProcessingException) =
    new IllegalArgumentException(
      s"JSON with contentType [$contentType] could not be decoded into a " +
      s"[${valueClass.getName}]: ${e.getMessage}. When changing the schema, make sure that changes are backwards compatible or apply a @Migration " +
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

  def isJson(bytesPayload: BytesPayload): Boolean =
    isJsonContentType(bytesPayload.contentType)

  def isJsonContentType(contentType: String): Boolean =
    // check both new and old typeUrl for compatibility, in case there are services with old type url stored in database
    contentType.startsWith(JsonContentTypePrefix) || contentType.startsWith(KalixJsonContentTypePrefix)

  private[akka] def replaceLegacyJsonPrefix(typeUrl: String): String =
    if (typeUrl.startsWith(KalixJsonContentTypePrefix))
      JsonContentTypePrefix + typeUrl.stripPrefix(KalixJsonContentTypePrefix)
    else typeUrl

  def stripJsonContentTypePrefix(contentType: String): String =
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

  def contentTypesFor(clz: Class[_]): List[String] = {
    if (clz == classOf[Array[Byte]]) {
      List(BytesPrimitive.fullName)
    } else {
      lookupTypeHint(clz).allTypeHints.map(JsonContentTypePrefix + _)
    }
  }

  private[akka] def removeVersion(typeName: String) = {
    typeName.split("#").head
  }

  private[akka] def encodeDynamicToAkkaByteString(key: String, value: Any): ByteString = {
    try {
      val dynamicJson = {
        // match all possible variants of createObjectNode.put method,
        // except the one accepting bytes[]
        value match {
          case v: String => objectMapper.createObjectNode.put(key, v)

          case v: Boolean           => objectMapper.createObjectNode.put(key, v)
          case v: java.lang.Boolean => objectMapper.createObjectNode.put(key, v)

          case v: Short           => objectMapper.createObjectNode.put(key, v)
          case v: java.lang.Short => objectMapper.createObjectNode.put(key, v)

          case v: Int                  => objectMapper.createObjectNode.put(key, v)
          case v: java.lang.Integer    => objectMapper.createObjectNode.put(key, v)
          case v: java.math.BigInteger => objectMapper.createObjectNode.put(key, v)

          case v: Long           => objectMapper.createObjectNode.put(key, v)
          case v: java.lang.Long => objectMapper.createObjectNode.put(key, v)

          case v: Float           => objectMapper.createObjectNode.put(key, v)
          case v: java.lang.Float => objectMapper.createObjectNode.put(key, v)

          case v: Double               => objectMapper.createObjectNode.put(key, v)
          case v: java.lang.Double     => objectMapper.createObjectNode.put(key, v)
          case v: java.math.BigDecimal => objectMapper.createObjectNode.put(key, v)
        }
      }
      ByteString.fromArrayUnsafe(objectMapper.writeValueAsBytes(dynamicJson))
    } catch {
      case ex: JsonProcessingException =>
        throw new IllegalArgumentException("Could not encode dynamic key/value as JSON", ex)
    }
  }

  private[akka] def encodeDynamicCollectionToAkkaByteString(key: String, values: java.util.Collection[_]): ByteString =
    try {
      val objectNode = objectMapper.createObjectNode
      val dynamicJson = objectNode.putArray(key)
      values.forEach(v => dynamicJson.add(v.toString))
      ByteString.fromArrayUnsafe(objectMapper.writeValueAsBytes(objectNode))
    } catch {
      case ex: JsonProcessingException =>
        throw new IllegalArgumentException("Could not encode dynamic key/values as JSON", ex)
    }
}
