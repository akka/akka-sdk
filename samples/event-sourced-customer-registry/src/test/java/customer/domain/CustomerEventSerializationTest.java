package customer.domain;

import akka.javasdk.JsonSupport;
import akka.javasdk.impl.serialization.JsonSerializer;
import akka.runtime.sdk.spi.BytesPayload;
import akka.util.ByteString;
import customer.domain.CustomerEvent.CustomerCreated;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static customer.domain.schemaevolution.CustomerEvent.AddressChanged;
import static customer.domain.schemaevolution.CustomerEvent.CustomerCreatedOld;
import static customer.domain.schemaevolution.CustomerEvent.NameChanged;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerEventSerializationTest {


  @Test
  public void shouldDeserializeWithMandatoryField() {
    //given
    BytesPayload serialized = JsonSupport.encodeToBytesPayload(new CustomerEvent.NameChanged("andre"));

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
    BytesPayload serialized = JsonSupport.encodeToBytesPayload(new CustomerEvent.AddressChanged(address));

    //when
    AddressChanged deserialized = JsonSupport.decodeJson(AddressChanged.class, serialized);

    //then
    assertEquals(address, deserialized.newAddress());
  }

  @Test
  public void shouldDeserializeWithStructureMigration() {
    //given
    BytesPayload serialized = JsonSupport.encodeToBytesPayload(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));

    //when
    CustomerCreated deserialized = JsonSupport.decodeJson(CustomerCreated.class, serialized);

    //then
    assertEquals("Wall Street", deserialized.address().street());
    assertEquals("New York", deserialized.address().city());
  }

  // tag::testing-deserialization[]
  @Test
  public void shouldDeserializeCustomerCreated_V0() {
    // tag::testing-deserialization-encoding[]
    BytesPayload serialized = JsonSupport.encodeToBytesPayload(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));
    String encodedBytes = new String(Base64.getEncoder().encode(serialized.bytes().toArray())); // <1>
    //save encodedBytes and serialized.contentType to a file
    // end::testing-deserialization-encoding[]

    //load encodedBytes and serialized.contentType from a file
    byte[] bytes = Base64.getDecoder().decode(encodedBytes.getBytes()); // <2>
    BytesPayload payload = new BytesPayload(ByteString.fromArray(bytes), serialized.contentType()); // <3>

    CustomerCreated deserialized = JsonSupport.decodeJson(CustomerCreated.class, payload); // <4>

    assertEquals("Wall Street", deserialized.address().street());
    assertEquals("New York", deserialized.address().city());
  }
  // end::testing-deserialization[]

}
