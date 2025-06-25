<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [Serialization](serialization.html)

<!-- </nav> -->

# Serialization

## <a href="about:blank#_jackson_serialization"></a> Jackson serialization

You need to make the messages, events, or the state of Akka components serializable with [Jackson](https://github.com/FasterXML/jackson). The same is true for inputs and outputs of HTTP Endpoints. There are two ways to do this.

1. If you are using Java [record](https://openjdk.org/jeps/395) then no annotation is needed. It just works. It’s as simple as using `record` instead of `class`. Akka leverages [Jackson](https://github.com/FasterXML/) under the hood and makes these records serializable for you.
2. If you are using Java `class` then you need to annotate them with the [proper Jackson annotation](https://github.com/FasterXML/jackson-annotations#usage-general).
Akka uses a predefined `Jackson` configuration, for serialization. Use the `JsonSupport` utility to update the `ObjectMapper` with your custom requirements. To minimize the number of `Jackson` annotations, Java classes are compiled with the `-parameters` flag.

[CustomerRegistrySetup.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/CustomerRegistrySetup.java)
```java
@Setup
public class CustomerRegistrySetup implements ServiceSetup {

  @Override
  public void onStartup() {
      JsonSupport.getObjectMapper()
            .configure(FAIL_ON_NULL_CREATOR_PROPERTIES, true); // (1)
  }
}
```

| **1** | Sets custom `ObjectMapper` configuration. |

## <a href="about:blank#_type_name"></a> Type name

It’s **highly recommended** to add a `@TypeName` annotation to all persistent classes: entity states, events, Workflow step inputs/results. Information about the type, persisted together with the JSON payload, is used to deserialize the payload and to route it to an appropriate `Subscription` or `View` handler. By default, a FQCN is used, which requires extra attention in case of renaming or repacking. Therefore, we recommend using a logical type name to simplify refactoring tasks. Migration from the old name is also possible, see [renaming class](about:blank#_renaming_class).

## <a href="about:blank#_schema_evolution"></a> Schema evolution

When using Event Sourcing, but also for rolling updates, schema evolution becomes an important aspect of your application development. A production-ready solution should be able to update any persisted models. The requirements as well as our own understanding of the business domain may (and will) change over time.

### <a href="about:blank#_removing_a_field"></a> Removing a field

Removing a field can be done without any migration code. The Jackson serializer will ignore properties that do not exist in the class.

### <a href="about:blank#_adding_an_optional_field"></a> Adding an optional field

Adding an optional field can be done without any migration code. The default value will be `Optional.empty` or `null` if the field is not wrapped with an `Optional` type.

Old class:

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/domain/CustomerEvent.java)
```java
record NameChanged(String newName) implements CustomerEvent {
}
```
New class with optional `oldName` and nullable `reason`.

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java)
```java
record NameChanged(String newName, Optional<String> oldName, String reason) implements CustomerEvent {
}
```

### <a href="about:blank#_adding_a_mandatory_field"></a> Adding a mandatory field

Let’s say we want to have a mandatory `reason` field. Always set to a some (non-null) value. One solution could be to override the constructor, but with more complex and nested types, this might quickly become a hard to follow solution.

Another approach is to use the `JsonMigration` extension that allows you to create a complex migration logic based on the payload version number.

[NameChangedMigration.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/NameChangedMigration.java)
```java
public class NameChangedMigration extends JsonMigration { // (1)

  @Override
  public int currentVersion() {
    return 1; // (2)
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    if (fromVersion < 1) { // (3)
      ObjectNode objectNode = ((ObjectNode) json);
      objectNode.set("reason", TextNode.valueOf("default reason")); // (4)
    }
    return json; // (5)
  }
}
```

| **1** | Migration must extend `JsonMigration` class. |
| **2** | Sets current version number. The first version, when no migration was used, is always 0. Increase this version number whenever you perform a change that is not backwards compatible without migration code. |
| **3** | Implements the transformation of the old JSON structure to the new JSON structure. |
| **4** | The JsonNode is mutable, so you can add and remove fields, or change values. Note that you have to cast to specific sub-classes such as `ObjectNode` and `ArrayNode` to get access to mutators. |
| **5** | Returns updated JSON matching the new class structure. |
The migration class must be linked to the updated model with the `@Migration` annotation.

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java)
```java
@Migration(NameChangedMigration.class) // (1)
record NameChanged(String newName, Optional<String> oldName, String reason) implements CustomerEvent {
}
```

| **1** | Links the migration implementation with the updated event. |

### <a href="about:blank#_renaming_a_field"></a> Renaming a field

Renaming a field is a very similar migration.

Old class:

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/domain/CustomerEvent.java)
```java
record AddressChanged(Address address) implements CustomerEvent {
}
```
New class:

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java)
```java
@Migration(AddressChangedMigration.class)
record AddressChanged(Address newAddress) implements CustomerEvent {
}
```
The migration implementation:

[AddressChangedMigration.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/AddressChangedMigration.java)
```java
public class AddressChangedMigration extends JsonMigration {

  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    if (fromVersion < 1) {
      ObjectNode objectNode = ((ObjectNode) json);
      JsonNode oldField = json.get("address"); // (1)
      objectNode.set("newAddress", oldField); // (2)
      objectNode.remove("address"); // (3)
    }
    return json;
  }
}
```

| **1** | Finds the old `address` field. |
| **2** | Updates the JSON tree with the `newAddress` field name. |
| **3** | Removes the old field. |

### <a href="about:blank#_changing_the_structure"></a> Changing the structure

Old class:

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java)
```java
record CustomerCreatedOld(String email, String name, String street, String city) implements CustomerEvent {
}
```
New class with the `Address` type:

[CustomerEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/domain/CustomerEvent.java)
```java
@Migration(CustomerCreatedMigration.class)
record CustomerCreated(String email, String name, Address address) implements CustomerEvent {
}
```
The migration implementation:

[CustomerCreatedMigration.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/domain/CustomerCreatedMigration.java)
```java
public class CustomerCreatedMigration extends JsonMigration {

  @Override
  public int currentVersion() {
    return 1;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    if (fromVersion == 0) {
      ObjectNode root = ((ObjectNode) json);
      ObjectNode address = root.with("address"); // (1)
      address.set("street", root.get("street"));
      address.set("city", root.get("city"));
      root.remove("city");
      root.remove("street");
    }
    return json;
  }
}
```

| **1** | Creates a new nested JSON object, with the data from the old schema. |

### <a href="about:blank#_renaming_class"></a> Renaming class

Renaming the class doesn’t require any additional work when @TypeName annotation is used. For other cases, the `JsonMigration` implementation can specify all old class names.

[AddressChangedMigration.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/AddressChangedMigration.java)
```java
public class AddressChangedMigration extends JsonMigration {

  @Override
  public int currentVersion() {
    return 1;
  }


  @Override
  public List<String> supportedClassNames() {
    return List.of("customer.domain.CustomerEvent$OldAddressChanged"); // (1)
  }

}
```

| **1** | Specifies the old event name. |

### <a href="about:blank#_testing"></a> Testing

It’s highly recommended to cover all schema changes with unit tests. In most cases it won’t be possible to reuse the same class for serialization and deserialization, since the model is different from version 0 to version N. One solution could be to create a byte array snapshot of each version and save it to a file. To generate the snapshot use `SerializationTestkit` utility.

[CustomerEventSerializationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/CustomerEventSerializationTest.java)
```java
byte[] serialized = SerializationTestkit.serialize(new CustomerCreatedOld("bob@lightbend.com", "bob", "Wall Street", "New York"));
var tmpDir = Files.createTempFile("customer-created-old", ".json");
// save serialized to a file and remove `CustomerCreatedOld`
Files.write(tmpDir.toAbsolutePath(), serialized); // (1)
```

| **1** | Save old class payload to a file. |
Test example:

[CustomerEventSerializationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/domain/CustomerEventSerializationTest.java)
```java
@Test
public void shouldDeserializeCustomerCreated_V0() throws IOException {
  // load serialized bytes and deserialize with the new schema
  var serialized = getClass().getResourceAsStream("/customer-created-old.json").readAllBytes(); // (1)
  CustomerCreated deserialized = SerializationTestkit.deserialize(CustomerCreated.class, serialized); // (2)

  assertEquals("Wall Street", deserialized.address().street());
  assertEquals("New York", deserialized.address().city());
}
```

| **1** | Loading old payload from a file. |
| **2** | Deserializing with the latest schema. |

<!-- <footer> -->
<!-- <nav> -->
[Setup and dependency injection](setup-and-dependency-injection.html) [Errors and failures](errors-and-failures.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->