package customer.domain;

import akka.javasdk.testkit.SerializationTestkit;
import customer.domain.CustomerEvent.CustomerCreated;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import static customer.domain.schemaevolution.CustomerEvent.AddressChanged;
import static customer.domain.schemaevolution.CustomerEvent.CustomerCreatedOld;
import static customer.domain.schemaevolution.CustomerEvent.NameChanged;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerEventSerializationTest {

  @Test
  public void shouldDeserializeWithMandatoryField() {
    //given
    byte[] serialized = SerializationTestkit.serialize(new CustomerEvent.NameChanged("andre"));

    //when
    NameChanged deserialized = SerializationTestkit.deserialize(NameChanged.class, serialized);

    //then
    assertEquals("andre", deserialized.newName());
    assertEquals(Optional.empty(), deserialized.oldName());
    assertEquals("default reason", deserialized.reason());
  }

  @Test
  public void shouldDeserializeWithChangedFieldName() {
    //given
    Address address = new Address("Wall Street", "New York");
    byte[] serialized = SerializationTestkit.serialize(new CustomerEvent.AddressChanged(address));

    //when
    AddressChanged deserialized = SerializationTestkit.deserialize(AddressChanged.class, serialized);

    //then
    assertEquals(address, deserialized.newAddress());
  }

  @Test
  public void shouldDeserializeWithStructureMigration() {
    //given
    byte[] serialized = SerializationTestkit.serialize(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));

    //when
    CustomerCreated deserialized = SerializationTestkit.deserialize(CustomerCreated.class, serialized);

    //then
    assertEquals("Wall Street", deserialized.address().street());
    assertEquals("New York", deserialized.address().city());
  }

  // tag::testing-deserialization[]
  @Test
  public void shouldDeserializeCustomerCreated_V0() throws IOException {
    // end::testing-deserialization[]
    // saveOldPayload();

    // tag::testing-deserialization[]
    // load serialized bytes and deserialize with the new schema
    var serialized = getClass().getResourceAsStream("/customer-created-old.json").readAllBytes(); //<1>
    CustomerCreated deserialized = SerializationTestkit.deserialize(CustomerCreated.class, serialized); // <2>

    assertEquals("Wall Street", deserialized.address().street());
    assertEquals("New York", deserialized.address().city());
  }
  // end::testing-deserialization[]

  private static void saveOldPayload() throws IOException {
    // tag::testing-deserialization-encoding[]
    byte[] serialized = SerializationTestkit.serialize(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));
    var tmpDir = Files.createTempFile("customer-created-old", ".json");
    // save serialized to a file and remove `CustomerCreatedOld`
    Files.write(tmpDir.toAbsolutePath(), serialized); // <1>
    // end::testing-deserialization-encoding[]
  }

}
