/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.protobuf;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity;
import akkajavasdk.protocol.SerializationTestProtos.CustomerCreated;
import akkajavasdk.protocol.SerializationTestProtos.CustomerEmailChanged;
import akkajavasdk.protocol.SerializationTestProtos.CustomerNameChanged;
import com.google.protobuf.GeneratedMessageV3;

/**
 * A consumer that subscribes to protobuf events from ProtobufCustomerEntity using a single handler
 * accepting the base GeneratedMessageV3 type. Proto event types are auto-resolved from the source
 * entity's @ProtoEventTypes annotation.
 */
@Component(id = "protobuf-customer-consumer")
@Consume.FromEventSourcedEntity(value = ProtobufCustomerEntity.class)
public class ProtobufCustomerConsumer extends Consumer {

  private final ComponentClient componentClient;

  public ProtobufCustomerConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(GeneratedMessageV3 event) {
    String entityId = messageContext().eventSubject().get();
    // Store the last event info in a KV entity so we can verify in tests
    String info =
        switch (event) {
          case CustomerCreated created -> "created:" + created.getName() + ":" + created.getEmail();
          case CustomerNameChanged nameChanged -> "nameChanged:" + nameChanged.getNewName();
          case CustomerEmailChanged emailChanged -> "emailChanged:" + emailChanged.getNewEmail();
          default -> "unknown:" + event.getClass().getSimpleName();
        };

    var result =
        componentClient
            .forKeyValueEntity(entityId)
            .method(ProtobufCustomerKvEntity::create)
            .invokeAsync(new ProtobufCustomerKvEntity.CreateCommand(info, "consumer-event"));
    return effects().asyncDone(result.thenApply(__ -> Done.getInstance()));
  }
}
