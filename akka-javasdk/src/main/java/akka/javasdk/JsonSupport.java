/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.Done;
import akka.javasdk.annotations.Migration;
import akka.javasdk.impl.AnySupport;
import akka.javasdk.impl.ByteStringEncoding;
import akka.runtime.sdk.spi.BytesPayload;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Optional;

public final class JsonSupport {

  // object mapper for HTTP endpoints and explicit serialization/deserialization
  // of objects in user code, customizable for by users, not used for "internal" serialization in component client, views etc.
  private static final ObjectMapper objectMapper = akka.javasdk.impl.serialization.JsonSerializer.newObjectMapperWithDefaults();

  /**
   * The Jackson ObjectMapper that is used for encoding and decoding JSON for HTTP endpoints
   * and HTTP requests.
   *
   * You may adjust its configuration, but that must only be performed before starting the service,
   * from {@link akka.javasdk.ServiceSetup#onStartup}.
   */
  public static ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  // internal serialization object, but using the public/configurable mapper
  private static akka.javasdk.impl.serialization.JsonSerializer jsonSerializer = new akka.javasdk.impl.serialization.JsonSerializer(objectMapper);

  private JsonSupport() {
  }

  /**
   * Encode the given value as JSON using Jackson and put the encoded string as bytes in a protobuf
   * Any with the type URL {@code "json.akka.io/[valueClassName]"}.
   *
   * <p>Note that if the serialized Any is published to a pub/sub topic that is consumed by an
   * external service using the class name suffix this introduces coupling as the internal class
   * name of this service becomes known to the outside of the service (and for exampe renaming it
   * may break existing consumers). For such cases consider using the overload with an explicit name
   * for the JSON type instead.
   *
   * @see {{encodeJson(T, String}}
   *
   * @deprecated Protobuf Any with JSON is not supported
   */
  @Deprecated
  public static <T> Any encodeJson(T value) {
    return encodeJson(value, value.getClass().getName());
  }

  /**
   * Encode the given value as JSON using Jackson and put the encoded string as bytes in a protobuf
   * Any with the type URL {@code "json.akka.io/[jsonType]"}.
   *
   * @param value    the object to encode as JSON, must be an instance of a class properly annotated
   *                 with the needed Jackson annotations.
   * @param jsonType A discriminator making it possible to identify which type of object is in the
   *                 JSON, useful for example when multiple different objects are passed through a pub/sub
   *                 topic.
   * @throws IllegalArgumentException if the given value cannot be turned into JSON
   *
   * @deprecated Protobuf Any with JSON is not supported
   */
  @Deprecated
  public static <T> Any encodeJson(T value, String jsonType) {
    try {
      ByteString bytes = encodeToBytes(value);
      ByteString encodedBytes = ByteStringEncoding.encodePrimitiveBytes(bytes);
      return Any.newBuilder().setTypeUrl(AnySupport.JsonTypeUrlPrefix() + jsonType).setValue(encodedBytes).build();
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
        "Could not encode [" + value.getClass().getName() + "] as JSON", ex);
    }
  }

  /**
   * @deprecated Use encodeToAkkaByteString
   */
  @Deprecated
  public static <T> ByteString encodeToBytes(T value) throws JsonProcessingException {
    return UnsafeByteOperations.unsafeWrap(
      objectMapper.writerFor(value.getClass()).writeValueAsBytes(value));
  }

  /**
   * Encode the given value as JSON using Jackson.
   *
   * @param value    the object to encode as JSON, must be an instance of a class properly annotated
   *                 with the needed Jackson annotations.
   * @throws IllegalArgumentException if the given value cannot be turned into JSON
   */
  public static <T> akka.util.ByteString encodeToAkkaByteString(T value) {
    try {
      return akka.util.ByteString.fromArrayUnsafe(objectMapper.writerFor(value.getClass()).writeValueAsBytes(value));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Could not encode [" + value.getClass().getName() + "] as JSON", ex);
    }
  }

  /**
   * @deprecated was only intended for internal use
   */
  @Deprecated
  public static akka.util.ByteString encodeDynamicToAkkaByteString(String key, String value) {
    try {
      ObjectNode dynamicJson = objectMapper.createObjectNode().put(key, value);
      return akka.util.ByteString.fromArrayUnsafe(objectMapper.writeValueAsBytes(dynamicJson));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Could not encode dynamic key/value as JSON", ex);
    }
  }

  /**
   * @deprecated was only intended for internal use
   */
  @Deprecated
  public static akka.util.ByteString encodeDynamicCollectionToAkkaByteString(String key, Collection<?> values) {
    try {
      ObjectNode objectNode = objectMapper.createObjectNode();
      ArrayNode dynamicJson = objectNode.putArray(key);
      values.forEach(v -> dynamicJson.add(v.toString()));
      return akka.util.ByteString.fromArrayUnsafe(objectMapper.writeValueAsBytes(objectNode));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Could not encode dynamic key/values as JSON", ex);
    }
  }

  /**
   * Decode the given bytes to an instance of T using Jackson. The bytes must be
   * the JSON string as bytes.
   *
   * @param valueClass The type of class to deserialize the object to, the class must have the
   *                   proper Jackson annotations for deserialization.
   * @param bytes      The bytes to deserialize.
   * @return The decoded object
   * @throws IllegalArgumentException if the given value cannot be decoded to a T
   *
   */
  public static <T> T decodeJson(Class<T> valueClass, akka.util.ByteString bytes) {
    return jsonSerializer.fromBytes(valueClass, new BytesPayload(bytes, jsonSerializer.contentTypeFor(valueClass)));
  }

  /**
   * Decode the given bytes to an instance of T using Jackson. The bytes must be
   * the JSON string as bytes.
   *
   * @param valueClass The type of class to deserialize the object to, the class must have the
   *                   proper Jackson annotations for deserialization.
   * @param bytes      The bytes to deserialize.
   * @return The decoded object
   * @throws IllegalArgumentException if the given value cannot be decoded to a T
   *
   */
  public static <T> T decodeJson(Class<T> valueClass, byte[] bytes) {
    return decodeJson(valueClass, akka.util.ByteString.fromArrayUnsafe(bytes));
  }

  /**
   * Decode the given protobuf Any object to an instance of T using Jackson. The object must have
   * the JSON string as bytes as value and a type URL starting with "json.akka.io/".
   *
   * @param valueClass The type of class to deserialize the object to, the class must have the
   *                   proper Jackson annotations for deserialization.
   * @param any        The protobuf Any object to deserialize.
   * @return The decoded object
   * @throws IllegalArgumentException if the given value cannot be decoded to a T
   *
   * @deprecated Protobuf Any with JSON is not supported
   */
  @Deprecated
  public static <T> T decodeJson(Class<T> valueClass, Any any) {
    if (!AnySupport.isJsonTypeUrl(any.getTypeUrl())) {
      throw new IllegalArgumentException(
          "Protobuf bytes with type url ["
              + any.getTypeUrl()
              + "] cannot be decoded as JSON, must start with ["
              + AnySupport.JsonTypeUrlPrefix()
              + "]");
    } else {
      try {
        ByteString decodedBytes = ByteStringEncoding.decodePrimitiveBytes(any.getValue());
        if (valueClass.getAnnotation(Migration.class) != null) {
          JsonMigration migration = valueClass.getAnnotation(Migration.class)
              .value()
              .getConstructor()
              .newInstance();
          int fromVersion = parseVersion(any.getTypeUrl());
          int currentVersion = migration.currentVersion();
          int supportedForwardVersion = migration.supportedForwardVersion();
          if (fromVersion < currentVersion) {
            return migrate(valueClass, decodedBytes, fromVersion, migration);
          } else if (fromVersion == currentVersion) {
            return objectMapper.readValue(decodedBytes.toByteArray(), valueClass);
          } else if (fromVersion <= supportedForwardVersion) {
            return migrate(valueClass, decodedBytes, fromVersion, migration);
          } else {
            throw new IllegalStateException("Migration version " + supportedForwardVersion + " is " +
                "behind version " + fromVersion + " of deserialized type [" + valueClass.getName() + "]");
          }
        } else {
          return objectMapper.readValue(decodedBytes.toByteArray(), valueClass);
        }
      } catch (JsonProcessingException e) {
        throw jsonProcessingException(valueClass, any, e);
      } catch (IOException | NoSuchMethodException | InstantiationException | IllegalAccessException |
               InvocationTargetException e) {
        throw genericDecodeException(valueClass, any, e);
      }
    }
  }

  /**
   * @deprecated Use decodeJson
   */
  @Deprecated
  public static <T> T parseBytes(byte[] bytes, Class<T> valueClass) throws IOException {
    return objectMapper.readValue(bytes, valueClass);
  }

  private static <T> IllegalArgumentException jsonProcessingException(Class<T> valueClass, Any any, JsonProcessingException e) {
    return new IllegalArgumentException(
        "JSON with type url ["
            + any.getTypeUrl()
            + "] could not be decoded into a ["
            + valueClass.getName()
            + "]. Make sure that changes are backwards compatible or apply a @Migration mechanism (https://doc.akka.io/java/serialization.html#_schema_evolution).",
        e);
  }

  private static <T> IllegalArgumentException genericDecodeException(Class<T> valueClass, Any any, Exception e) {
    return new IllegalArgumentException(
        "JSON with type url ["
            + any.getTypeUrl()
            + "] could not be decoded into a ["
            + valueClass.getName()
            + "]",
        e);
  }

  private static <T> T migrate(Class<T> valueClass, ByteString decodedBytes, int fromVersion, JsonMigration jsonMigration) throws IOException {
    JsonNode jsonNode = objectMapper.readTree(decodedBytes.toByteArray());
    JsonNode newJsonNode = jsonMigration.transform(fromVersion, jsonNode);
    return objectMapper.treeToValue(newJsonNode, valueClass);
  }

  private static int parseVersion(String typeUrl) {
    int versionSeparatorIndex = typeUrl.lastIndexOf("#");
    if (versionSeparatorIndex > 0) {
      String maybeVersion = typeUrl.substring(versionSeparatorIndex + 1);
      return Integer.parseInt(maybeVersion);
    } else {
      return 0;
    }
  }


  /**
   * @deprecated Protobuf Any with JSON is not supported
   */
  @Deprecated
  public static <T, C extends Collection<T>> C decodeJsonCollection(Class<T> valueClass, Class<C> collectionType, Any any) {
    if (!AnySupport.isJsonTypeUrl(any.getTypeUrl())) {
      throw new IllegalArgumentException(
          "Protobuf bytes with type url ["
              + any.getTypeUrl()
              + "] cannot be decoded as JSON, must start with ["
              + AnySupport.JsonTypeUrlPrefix()
              + "]");
    } else {
      try {
        ByteString decodedBytes = ByteStringEncoding.decodePrimitiveBytes(any.getValue());
        var typeRef = objectMapper.getTypeFactory().constructCollectionType(collectionType, valueClass);
        return objectMapper.readValue(decodedBytes.toByteArray(), typeRef);
      } catch (JsonProcessingException e) {
        throw jsonProcessingException(valueClass, any, e);
      } catch (IOException e) {
        throw genericDecodeException(valueClass, any, e);
      }
    }
  }

  /**
   * @deprecated was only intended for internal use
   */
  @Deprecated
  public static <T, C extends Collection<T>> C decodeJsonCollection(Class<T> valueClass, Class<C> collectionType, akka.util.ByteString bytes) {
    return jsonSerializer.fromBytes(valueClass, collectionType, new BytesPayload(bytes, jsonSerializer.contentTypeFor(valueClass)));
  }

  /**
   * @deprecated was only intended for internal use
   */
  @Deprecated
  public static <T, C extends Collection<T>> C decodeJsonCollection(Class<T> valueClass, Class<C> collectionType, byte[] bytes) {
    return decodeJsonCollection(valueClass, collectionType, akka.util.ByteString.fromArrayUnsafe(bytes));
  }

  /**
   * Decode the given protobuf Any to an instance of T using Jackson but only if the suffix of the
   * type URL matches the given jsonType.
   *
   * @return An Optional containing the successfully decoded value or an empty Optional if the type
   *     suffix does not match.
   * @throws IllegalArgumentException if the suffix matches but the Any cannot be parsed into a T
   *
   * @deprecated Protobuf Any with JSON is not supported
   */
  @Deprecated
  public static <T> Optional<T> decodeJson(Class<T> valueClass, String jsonType, Any any) {
    if (any.getTypeUrl().endsWith(jsonType)) {
      return Optional.of(decodeJson(valueClass, any));
    } else {
      return Optional.empty();
    }
  }

}

/**
 * @deprecated Not indented for public use and no longer used internally
 */
@Deprecated
class DoneSerializer extends JsonSerializer<Done> {

  @Override
  public void serialize(Done value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
    gen.writeStartObject();
    gen.writeEndObject();
  }

  @Override
  public void serializeWithType(Done value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
    typeSer.writeTypePrefixForObject(value, gen);
    gen.writeFieldName("value");
    gen.writeStartObject();
    gen.writeEndObject();
    typeSer.writeTypeSuffixForObject(value, gen);
  }
}

/**
 * @deprecated Not indented for public use and no longer used internally
 */
@Deprecated
class DoneDeserializer extends JsonDeserializer<Done> {

  @Override
  public Done deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (p.currentToken() == JsonToken.START_OBJECT && p.nextToken() == JsonToken.END_OBJECT) {
      return Done.getInstance();
    } else {
      throw JsonMappingException.from(ctxt, "Cannot deserialize Done class, expecting empty object '{}'");
    }
  }

  @Override
  public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
    return typeDeserializer.deserializeTypedFromObject(p, ctxt);
  }
}
