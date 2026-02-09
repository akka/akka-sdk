/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.protobuf;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity;
import akkajavasdk.protocol.SerializationTestProtos.CustomerState;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;

/**
 * A view that uses protobuf messages for state, query parameter, and return value. This exercises
 * protobuf-as-JSON serialization for views (which store data as JSONB).
 */
@Component(id = "protobuf_customer_state_view")
public class ProtobufCustomerStateView extends View {

  @Consume.FromKeyValueEntity(ProtobufCustomerKvEntity.class)
  public static class Customers extends TableUpdater<CustomerState> {}

  @Query("SELECT * FROM customers WHERE name = :text")
  public QueryEffect<CustomerState> getCustomerByName(SimpleMessage params) {
    return queryResult();
  }
}
