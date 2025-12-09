/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-with-empty-table-updater")
public class ValidViewWithEmptyTableUpdater extends View {

  public static class OrderState {
    public String orderId;
    public String customerId;
  }

  @Query("SELECT * FROM orders")
  public QueryEffect<OrderState> getOrders() {
    return null;
  }

  // Empty TableUpdater - passthrough scenario where state type matches row type
  // This is valid for KVE/Workflow subscriptions when no transformation is needed
  // The MyOrderEntity has OrderState as its state type, which matches the row type
  @Consume.FromKeyValueEntity(MyOrderEntity.class)
  public static class Orders extends TableUpdater<OrderState> {}

  public static class MyOrderEntity extends KeyValueEntity<OrderState> {
    @Override
    public OrderState emptyState() {
      return new OrderState();
    }
  }
}
