/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.valueentities.customer.CustomerEntity;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Consume;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

import java.time.Instant;
import java.util.List;


@ViewId("view_customers_by_creation_time")
@Table("customers_by_creation_time")
@Consume.FromValueEntity(CustomerEntity.class)
public class CustomerByCreationTime extends View<CustomerEntity.Customer> {

  public record CustomerList(List<CustomerEntity.Customer> customers){}
  public record QueryParameters(Instant createdOn) {}

  @Query("SELECT * as customers FROM customers_by_creation_time WHERE createdOn >= :createdOn")
  public CustomerList getCustomerByTime(QueryParameters params) {
    return null;
  }

}

