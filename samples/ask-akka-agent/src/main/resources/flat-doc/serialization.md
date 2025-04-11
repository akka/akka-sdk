# Serialization

## Jackson serialization

You need to make the messages, events, or the state of Akka components serializable with [Jackson, window="new"](https://github.com/FasterXML/jackson). The same is true for inputs and outputs of HTTP Endpoints. There are two ways to do this.

1. If you are using Java [record, window="new"](https://openjdk.org/jeps/395) then no annotation is needed. It just works. It’s as simple as using `record` instead of `class`. Akka leverages [Jackson](https://github.com/FasterXML/) under the hood and makes these records serializable for you.
2. If you are using Java `class` then you need to annotate them with the [proper Jackson annotation, window="new"](https://github.com/FasterXML/jackson-annotations#usage-general).

Akka uses a predefined `Jackson` configuration, for serialization. Use the `JsonSupport` utility to update the `ObjectMapper` with your custom requirements. To minimize the number of `Jackson` annotations, Java classes are compiled with the `-parameters` flag.
**{sample-base-url}/event-sourced-customer-registry/src/main/java/customer/CustomerRegistrySetup.java[CustomerRegistrySetup.java]**


```
1. Sets custom `ObjectMapper` configuration.

## Type name

It’s **highly recommended** to add a `@TypeName` annotation to all persistent classes: entity states, events, Workflow step inputs/results. Information about the type, persisted together with the JSON payload, is used to deserialize the payload and to route it to an appropriate `Subscription` or `View` handler. By default, a FQCN is used, which requires extra attention in case of renaming or repacking. Therefore, we recommend using a logical type name to simplify refactoring tasks. Migration from the old name is also possible, see [renaming class](serialization.adoc#_renaming_class).

## Schema evolution

When using Event Sourcing, but also for rolling updates, schema evolution becomes an important aspect of your application development. A production-ready solution should be able to update any persisted models. The requirements as well as our own understanding of the business domain may (and will) change over time.

### Removing a field

Removing a field can be done without any migration code. The Jackson serializer will ignore properties that do not exist in the class.

### Adding an optional field

Adding an optional field can be done without any migration code. The default value will be `Optional.empty` or `null` if the field is not wrapped with an `Optional` type.

Old class:

**{sample-base-url}/event-sourced-customer-registry/src/main/java/customer/domain/CustomerEvent.java[CustomerEvent.java]**


```

New class with optional `oldName` and nullable `reason`.

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java[CustomerEvent.java]**


```

### Adding a mandatory field

Let’s say we want to have a mandatory `reason` field. Always set to a some (non-null) value. One solution could be to override the constructor, but with more complex and nested types, this might quickly become a hard to follow solution.

Another approach is to use the `JsonMigration` extension that allows you to create a complex migration logic based on the payload version number.

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/NameChangedMigration.java[NameChangedMigration.java]**


```
1. Migration must extend `JsonMigration` class.
2. Sets current version number. The first version, when no migration was used, is always 0. Increase this version number whenever you perform a change that is not backwards compatible without migration code.
3. Implements the transformation of the old JSON structure to the new JSON structure.
4. The JsonNode is mutable, so you can add and remove fields, or change values. Note that you have to cast to specific sub-classes such as `ObjectNode` and `ArrayNode` to get access to mutators.
5. Returns updated JSON matching the new class structure.

The migration class must be linked to the updated model with the `@Migration` annotation.

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java[CustomerEvent.java]**


```
1. Links the migration implementation with the updated event.

### Renaming a field

Renaming a field is a very similar migration.

Old class:

**{sample-base-url}/event-sourced-customer-registry/src/main/java/customer/domain/CustomerEvent.java[CustomerEvent.java]**


```

New class:

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java[CustomerEvent.java]**


```

The migration implementation:

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/AddressChangedMigration.java[AddressChangedMigration.java]**


```
1. Finds the old `address` field.
2. Updates the JSON tree with the `newAddress` field name.
3. Removes the old field.

### Changing the structure

Old class:

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/CustomerEvent.java[CustomerEvent.java]**


```

New class with the `Address` type:

**{sample-base-url}/event-sourced-customer-registry/src/main/java/customer/domain/CustomerEvent.java[CustomerEvent.java]**


```

The migration implementation:

**{sample-base-url}/event-sourced-customer-registry/src/main/java/customer/domain/CustomerCreatedMigration.java[CustomerCreatedMigration.java]**


```
1. Creates a new nested JSON object, with the data from the old schema.

### Renaming class

Renaming the class doesn’t require any additional work when @TypeName annotation is used. For other cases, the `JsonMigration` implementation can specify all old class names.

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/schemaevolution/AddressChangedMigration.java[AddressChangedMigration.java]**


```
1. Specifies the old event name.

### Testing

It’s highly recommended to cover all schema changes with unit tests. In most cases it won’t be possible to reuse the same class for serialization and deserialization, since the model is different from version 0 to version N. One solution could be to create a byte array snapshot of each version and save it to a file. To generate the snapshot use `SerializationTestkit` utility.

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/CustomerEventSerializationTest.java[CustomerEventSerializationTest.java]**


```
1. Save old class payload to a file.

Test example:

**{sample-base-url}/event-sourced-customer-registry/src/test/java/customer/domain/CustomerEventSerializationTest.java[CustomerEventSerializationTest.java]**


```
1. Loading old payload from a file.
2. Deserializing with the latest schema.
