package customer.domain;

import akka.javasdk.JsonSupport;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static customer.domain.schemaevolution.CustomerEvent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerEventSerializationTest {

  @Test
  public void shouldDeserializeWithMandatoryField() throws JsonProcessingException {
    //given
    ByteString serialized = JsonSupport.encodeToAkkaByteString(new CustomerEvent.NameChanged("andre"));

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
    ByteString serialized = JsonSupport.encodeToAkkaByteString(new CustomerEvent.AddressChanged(address));

    //when
    AddressChanged deserialized = JsonSupport.decodeJson(AddressChanged.class, serialized);

    //then
    assertEquals(address, deserialized.newAddress());
  }

  @Test
  public void shouldDeserializeWithStructureMigration() {
    //given
    ByteString serialized = JsonSupport.encodeToAkkaByteString(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));

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
    ByteString serialized = JsonSupport.encodeToAkkaByteString(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));
    String encodedBytes = serialized.encodeBase64().utf8String(); // <1>
    // end::testing-deserialization-encoding[]

    ByteString decodedBytes = ByteString.fromString(encodedBytes).decodeBase64(); // <2>

    CustomerEvent.CustomerCreated deserialized = JsonSupport.decodeJson(CustomerEvent.CustomerCreated.class,
      decodedBytes); // <3>

    assertEquals("Wall Street", deserialized.address().street());
    assertEquals("New York", deserialized.address().city());
  }
  // end::testing-deserialization[]

}
