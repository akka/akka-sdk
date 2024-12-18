package customer.domain;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import akka.javasdk.JsonSupport;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static customer.domain.schemaevolution.CustomerEvent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerEventSerializationTest {

  @Test
  public void shouldDeserializeWithMandatoryField() {
    //given
    Any serialized = JsonSupport.encodeJson(new CustomerEvent.NameChanged("andre"));

    //when
    NameChanged deserialized = JsonSupport.decodeJson(NameChanged.class, serialized);

    //then
    assertEquals("andre", deserialized.newName());
    assertEquals(Optional.empty(), deserialized.oldName());
    assertEquals("default reason", deserialized.reason());
  }

  @Test
  public void shouldDeserializeWithChangedFieldName() {
    //given
    Address address = new Address("Wall Street", "New York");
    Any serialized = JsonSupport.encodeJson(new CustomerEvent.AddressChanged(address));

    //when
    AddressChanged deserialized = JsonSupport.decodeJson(AddressChanged.class, serialized);

    //then
    assertEquals(address, deserialized.newAddress());
  }

  @Test
  public void shouldDeserializeWithStructureMigration() {
    //given
    Any serialized = JsonSupport.encodeJson(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));

    //when
    CustomerEvent.CustomerCreated deserialized = JsonSupport.decodeJson(CustomerEvent.CustomerCreated.class, serialized);

    //then
    assertEquals("Wall Street", deserialized.address().street());
    assertEquals("New York", deserialized.address().city());
  }

  // tag::testing-deserialization[]
  @Test
  public void shouldDeserializeCustomerCreated_V0() throws InvalidProtocolBufferException {
    // tag::testing-deserialization-encoding[]
    Any serialized = JsonSupport.encodeJson(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));
    String encodedBytes = new String(Base64.getEncoder().encode(serialized.toByteArray())); // <1>
    // end::testing-deserialization-encoding[]

    byte[] bytes = Base64.getDecoder().decode(encodedBytes.getBytes()); // <2>
    Any serializedAny = Any.parseFrom(ByteString.copyFrom(bytes)); // <3>

    CustomerEvent.CustomerCreated deserialized = JsonSupport.decodeJson(CustomerEvent.CustomerCreated.class,
      serializedAny); // <4>

    assertEquals("Wall Street", deserialized.address().street());
    assertEquals("New York", deserialized.address().city());
  }
  // end::testing-deserialization[]

}
