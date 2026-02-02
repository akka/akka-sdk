/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.protobuf;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity;
import akkajavasdk.protocol.SerializationTestProtos.CustomerCreated;
import akkajavasdk.protocol.SerializationTestProtos.CustomerEmailChanged;
import akkajavasdk.protocol.SerializationTestProtos.CustomerNameChanged;
import com.google.protobuf.GeneratedMessageV3;
import java.util.Optional;

/**
 * A view that subscribes to protobuf events from ProtobufCustomerEntity using a single handler
 * accepting the base GeneratedMessageV3 type. Proto event types are auto-resolved from the source
 * entity's @ProtoEventTypes annotation.
 */
@Component(id = "protobuf_customers_by_name")
public class ProtobufCustomersByNameView extends View {

  public record CustomerRow(String customerId, String name, String email) {}

  public record QueryByName(String name) {}

  @Consume.FromEventSourcedEntity(ProtobufCustomerEntity.class)
  public static class Customers extends TableUpdater<CustomerRow> {

    public Effect<CustomerRow> onEvent(GeneratedMessageV3 event) {
      return switch (event) {
        case CustomerCreated created ->
            effects()
                .updateRow(
                    new CustomerRow(
                        created.getCustomerId(), created.getName(), created.getEmail()));
        case CustomerNameChanged nameChanged ->
            effects()
                .updateRow(
                    new CustomerRow(
                        rowState().customerId(), nameChanged.getNewName(), rowState().email()));
        case CustomerEmailChanged emailChanged ->
            effects()
                .updateRow(
                    new CustomerRow(
                        rowState().customerId(), rowState().name(), emailChanged.getNewEmail()));
        default -> effects().ignore();
      };
    }
  }

  @Query("SELECT * FROM customers WHERE name = :name")
  public QueryEffect<Optional<CustomerRow>> getCustomerByName(QueryByName params) {
    return queryResult();
  }
}
