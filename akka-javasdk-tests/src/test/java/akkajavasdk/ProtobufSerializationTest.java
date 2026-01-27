/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.impl.serialization.Serializer;
import akka.javasdk.testkit.SerializationTestkit;
import akkajavasdk.protocol.SerializationTestProtos.*;
import akkajavasdk.protocol.SerializationTestProtos.NestedMessage.*;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Tests for protobuf serialization support in the Akka SDK. */
public class ProtobufSerializationTest {

  private final Serializer serializer = new Serializer();

  // ==================== Simple Message Tests ====================

  @Test
  public void shouldSerializeAndDeserializeSimpleMessage() {
    var original =
        SimpleMessage.newBuilder().setText("hello world").setNumber(42).setFlag(true).build();

    var bytesPayload = serializer.toBytes(original);

    assertThat(bytesPayload.contentType())
        .isEqualTo("type.googleapis.com/akkajavasdk.serialization.SimpleMessage");
    assertThat(bytesPayload.bytes().isEmpty()).isFalse();

    var deserialized = serializer.fromBytes(SimpleMessage.class, bytesPayload);
    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getText()).isEqualTo("hello world");
    assertThat(deserialized.getNumber()).isEqualTo(42);
    assertThat(deserialized.getFlag()).isTrue();
  }

  @Test
  public void shouldHandleEmptySimpleMessage() {
    var empty = SimpleMessage.newBuilder().build();

    var bytesPayload = serializer.toBytes(empty);
    var deserialized = serializer.fromBytes(SimpleMessage.class, bytesPayload);

    assertThat(deserialized.getText()).isEmpty();
    assertThat(deserialized.getNumber()).isEqualTo(0);
    assertThat(deserialized.getFlag()).isFalse();
  }

  // ==================== Scalar Types Tests ====================

  @Test
  public void shouldSerializeAllScalarTypes() {
    var original =
        ScalarTypesMessage.newBuilder()
            .setDoubleValue(3.14159)
            .setFloatValue(2.71828f)
            .setInt32Value(Integer.MAX_VALUE)
            .setInt64Value(Long.MAX_VALUE)
            .setUint32Value(Integer.MAX_VALUE)
            .setUint64Value(Long.MAX_VALUE)
            .setSint32Value(Integer.MIN_VALUE)
            .setSint64Value(Long.MIN_VALUE)
            .setFixed32Value(123456)
            .setFixed64Value(123456789L)
            .setSfixed32Value(-123456)
            .setSfixed64Value(-123456789L)
            .setBoolValue(true)
            .setStringValue("test string")
            .setBytesValue(ByteString.copyFromUtf8("test bytes"))
            .build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(ScalarTypesMessage.class, bytesPayload);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getDoubleValue()).isEqualTo(3.14159);
    assertThat(deserialized.getStringValue()).isEqualTo("test string");
    assertThat(deserialized.getBytesValue().toStringUtf8()).isEqualTo("test bytes");
  }

  // ==================== Nested Message Tests ====================

  @Test
  public void shouldSerializeNestedMessage() {
    var address =
        Address.newBuilder()
            .setStreet("123 Main St")
            .setCity("Anytown")
            .setCountry("USA")
            .setZipCode(12345)
            .build();

    var phone1 = PhoneNumber.newBuilder().setNumber("555-1234").setType(PhoneType.MOBILE).build();

    var phone2 = PhoneNumber.newBuilder().setNumber("555-5678").setType(PhoneType.WORK).build();

    var original =
        NestedMessage.newBuilder()
            .setId("customer-123")
            .setAddress(address)
            .addPhones(phone1)
            .addPhones(phone2)
            .build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(NestedMessage.class, bytesPayload);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getId()).isEqualTo("customer-123");
    assertThat(deserialized.getAddress().getStreet()).isEqualTo("123 Main St");
    assertThat(deserialized.getPhonesList()).hasSize(2);
    assertThat(deserialized.getPhones(0).getType()).isEqualTo(PhoneType.MOBILE);
  }

  // ==================== Repeated Fields Tests ====================

  @Test
  public void shouldSerializeRepeatedFields() {
    var msg1 = SimpleMessage.newBuilder().setText("msg1").setNumber(1).build();
    var msg2 = SimpleMessage.newBuilder().setText("msg2").setNumber(2).build();

    var original =
        RepeatedFieldsMessage.newBuilder()
            .addAllStrings(Arrays.asList("a", "b", "c"))
            .addAllNumbers(Arrays.asList(1, 2, 3, 4, 5))
            .addMessages(msg1)
            .addMessages(msg2)
            .build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(RepeatedFieldsMessage.class, bytesPayload);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getStringsList()).containsExactly("a", "b", "c");
    assertThat(deserialized.getNumbersList()).containsExactly(1, 2, 3, 4, 5);
    assertThat(deserialized.getMessagesList()).hasSize(2);
  }

  @Test
  public void shouldHandleEmptyRepeatedFields() {
    var original = RepeatedFieldsMessage.newBuilder().build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(RepeatedFieldsMessage.class, bytesPayload);

    assertThat(deserialized.getStringsList()).isEmpty();
    assertThat(deserialized.getNumbersList()).isEmpty();
    assertThat(deserialized.getMessagesList()).isEmpty();
  }

  // ==================== Map Fields Tests ====================

  @Test
  public void shouldSerializeMapFields() {
    var msg1 = SimpleMessage.newBuilder().setText("value1").build();
    var msg2 = SimpleMessage.newBuilder().setText("value2").build();

    var original =
        MapFieldsMessage.newBuilder()
            .putStringMap("key1", "value1")
            .putStringMap("key2", "value2")
            .putIntMap("count1", 100)
            .putIntMap("count2", 200)
            .putMessageMap(1, msg1)
            .putMessageMap(2, msg2)
            .build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(MapFieldsMessage.class, bytesPayload);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getStringMapMap()).containsEntry("key1", "value1");
    assertThat(deserialized.getIntMapMap()).containsEntry("count1", 100);
    assertThat(deserialized.getMessageMapMap().get(1).getText()).isEqualTo("value1");
  }

  // ==================== Oneof Tests ====================

  @Test
  public void shouldSerializeOneofWithTextPayload() {
    var original = OneofMessage.newBuilder().setId("oneof-1").setTextPayload("text value").build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(OneofMessage.class, bytesPayload);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getPayloadCase()).isEqualTo(OneofMessage.PayloadCase.TEXT_PAYLOAD);
    assertThat(deserialized.getTextPayload()).isEqualTo("text value");
  }

  @Test
  public void shouldSerializeOneofWithNumberPayload() {
    var original = OneofMessage.newBuilder().setId("oneof-2").setNumberPayload(999).build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(OneofMessage.class, bytesPayload);

    assertThat(deserialized.getPayloadCase()).isEqualTo(OneofMessage.PayloadCase.NUMBER_PAYLOAD);
    assertThat(deserialized.getNumberPayload()).isEqualTo(999);
  }

  @Test
  public void shouldSerializeOneofWithMessagePayload() {
    var innerMsg = SimpleMessage.newBuilder().setText("inner").setNumber(42).build();
    var original = OneofMessage.newBuilder().setId("oneof-3").setMessagePayload(innerMsg).build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(OneofMessage.class, bytesPayload);

    assertThat(deserialized.getPayloadCase()).isEqualTo(OneofMessage.PayloadCase.MESSAGE_PAYLOAD);
    assertThat(deserialized.getMessagePayload().getText()).isEqualTo("inner");
  }

  // ==================== Optional Fields Tests ====================

  @Test
  public void shouldSerializeOptionalFieldsWhenSet() {
    var innerMsg = SimpleMessage.newBuilder().setText("optional").build();
    var original =
        OptionalFieldsMessage.newBuilder()
            .setOptionalString("present")
            .setOptionalInt(123)
            .setOptionalMessage(innerMsg)
            .build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(OptionalFieldsMessage.class, bytesPayload);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.hasOptionalString()).isTrue();
    assertThat(deserialized.getOptionalString()).isEqualTo("present");
    assertThat(deserialized.hasOptionalInt()).isTrue();
    assertThat(deserialized.getOptionalInt()).isEqualTo(123);
    assertThat(deserialized.hasOptionalMessage()).isTrue();
  }

  @Test
  public void shouldSerializeOptionalFieldsWhenNotSet() {
    var original = OptionalFieldsMessage.newBuilder().build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(OptionalFieldsMessage.class, bytesPayload);

    assertThat(deserialized.hasOptionalString()).isFalse();
    assertThat(deserialized.hasOptionalInt()).isFalse();
    assertThat(deserialized.hasOptionalMessage()).isFalse();
  }

  // ==================== Enum Tests ====================

  @Test
  public void shouldSerializeEnumField() {
    var original = StatusMessage.newBuilder().setId("status-1").setStatus(Status.ACTIVE).build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(StatusMessage.class, bytesPayload);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldHandleDefaultEnumValue() {
    var original = StatusMessage.newBuilder().setId("status-2").build();

    var bytesPayload = serializer.toBytes(original);
    var deserialized = serializer.fromBytes(StatusMessage.class, bytesPayload);

    assertThat(deserialized.getStatus()).isEqualTo(Status.UNKNOWN);
  }

  // ==================== Entity State/Event Tests ====================

  @Test
  public void shouldSerializeCustomerCreatedEvent() {
    var event =
        CustomerCreated.newBuilder()
            .setCustomerId("cust-123")
            .setName("John Doe")
            .setEmail("john@example.com")
            .build();

    var bytesPayload = serializer.toBytes(event);
    assertThat(bytesPayload.contentType())
        .isEqualTo("type.googleapis.com/akkajavasdk.serialization.CustomerCreated");

    var deserialized = serializer.fromBytes(CustomerCreated.class, bytesPayload);
    assertThat(deserialized).isEqualTo(event);
  }

  @Test
  public void shouldSerializeCustomerState() {
    var state =
        CustomerState.newBuilder()
            .setCustomerId("cust-456")
            .setName("Jane Smith")
            .setEmail("jane@example.com")
            .setStatus(Status.ACTIVE)
            .addTags("premium")
            .addTags("verified")
            .build();

    var bytesPayload = serializer.toBytes(state);
    var deserialized = serializer.fromBytes(CustomerState.class, bytesPayload);

    assertThat(deserialized).isEqualTo(state);
    assertThat(deserialized.getTagsList()).containsExactly("premium", "verified");
  }

  // ==================== JSON Serialization Tests ====================

  @Test
  public void shouldSerializeProtobufToJsonFormat() {
    var original =
        SimpleMessage.newBuilder().setText("json test").setNumber(100).setFlag(true).build();

    var bytesPayload = serializer.toBytesAsJson(original);

    assertThat(bytesPayload.contentType())
        .isEqualTo("json.akka.io/akkajavasdk.serialization.SimpleMessage");
    var jsonString = bytesPayload.bytes().utf8String();
    assertThat(jsonString).contains("\"text\"");
    assertThat(jsonString).contains("\"json test\"");
    assertThat(jsonString).contains("\"number\"");
    assertThat(jsonString).contains("100");
  }

  @Test
  public void shouldSerializeNestedMessageToJson() {
    var address = Address.newBuilder().setStreet("456 Oak Ave").setCity("Springfield").build();

    var original = NestedMessage.newBuilder().setId("nested-json").setAddress(address).build();

    var bytesPayload = serializer.toBytesAsJson(original);
    var jsonString = bytesPayload.bytes().utf8String();

    assertThat(jsonString).contains("\"id\"");
    assertThat(jsonString).contains("\"nested-json\"");
    assertThat(jsonString).contains("\"address\"");
    assertThat(jsonString).contains("\"street\"");
    assertThat(jsonString).contains("\"456 Oak Ave\"");
  }

  // ==================== Content Type Detection Tests ====================

  @Test
  public void shouldDetectProtobufContentType() {
    var protoPayload = serializer.toBytes(SimpleMessage.newBuilder().setText("test").build());
    assertThat(serializer.isProtobuf(protoPayload)).isTrue();
    assertThat(serializer.isJson(protoPayload)).isFalse();
  }

  @Test
  public void shouldReturnCorrectContentTypeForProtobufClass() {
    var contentType = serializer.contentTypeFor(SimpleMessage.class);
    assertThat(contentType)
        .isEqualTo("type.googleapis.com/akkajavasdk.serialization.SimpleMessage");
  }

  @Test
  public void shouldReturnCorrectContentTypesListForProtobufClass() {
    var contentTypes = serializer.contentTypesFor(CustomerState.class);
    // Convert Scala List to Java for assertion
    assertThat(scala.jdk.javaapi.CollectionConverters.asJava(contentTypes))
        .contains("type.googleapis.com/akkajavasdk.serialization.CustomerState");
  }

  // ==================== Error Cases Tests ====================

  @Test
  public void shouldThrowOnNullValue() {
    assertThatThrownBy(() -> serializer.toBytes(null)).isInstanceOf(NullPointerException.class);
  }

  // ==================== SerializationTestkit Integration Tests ====================

  @Test
  public void shouldWorkWithSerializationTestkit() {
    var original = SimpleMessage.newBuilder().setText("testkit test").setNumber(777).build();

    byte[] serialized = SerializationTestkit.serialize(original);
    var deserialized = SerializationTestkit.deserialize(SimpleMessage.class, serialized);

    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  public void shouldRoundTripComplexMessageWithTestkit() {
    var address =
        Address.newBuilder()
            .setStreet("789 Pine Rd")
            .setCity("Metropolis")
            .setCountry("USA")
            .setZipCode(99999)
            .build();

    var original =
        NestedMessage.newBuilder()
            .setId("testkit-nested")
            .setAddress(address)
            .addPhones(
                PhoneNumber.newBuilder().setNumber("111-222-3333").setType(PhoneType.HOME).build())
            .build();

    byte[] serialized = SerializationTestkit.serialize(original);
    var deserialized = SerializationTestkit.deserialize(NestedMessage.class, serialized);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.getAddress().getCity()).isEqualTo("Metropolis");
  }

  @Test
  public void shouldRoundTripEntityStateWithTestkit() {
    var state =
        CustomerState.newBuilder()
            .setCustomerId("testkit-cust")
            .setName("Test User")
            .setEmail("test@example.com")
            .setStatus(Status.PENDING)
            .addTags("test")
            .build();

    byte[] serialized = SerializationTestkit.serialize(state);
    var deserialized = SerializationTestkit.deserialize(CustomerState.class, serialized);

    assertThat(deserialized).isEqualTo(state);
  }
}
