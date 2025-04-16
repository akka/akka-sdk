## Identifying the Entity

In order to interact with an Entity in Akka, we need to assign a **component id** and an instance **id**:

* **component id** is a unique identifier for all entities of a given type. To define the component id, the entity class must be annotated with `@ComponentId` and have a unique and stable identifier assigned.
* **id**, on the other hand, is unique per instance. The entity id is used in the component client when calling the entity from for example an Endpoint.

As an example, an entity representing a customer could have the **component id** `customer` and a customer entity for a specific customer could have the UUID instance **id** `8C59E488-B6A8-4E6D-92F3-760315283B6E`.

**📌 NOTE**\
The component id and entity id cannot contain the reserved character `|`, because that is used internally by Akka as a separator.
