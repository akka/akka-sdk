/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ViewId;
import akka.platform.javasdk.view.View;

import java.time.Instant;
import java.util.List;


@ViewId("view_customers_by_creation_time")
@Table("customers_by_creation_time")
@Consume.FromKeyValueEntity(CustomerEntity.class)
public class CustomerByCreationTime extends View<CustomerEntity.Customer> {

  public record CustomerList(List<CustomerEntity.Customer> customers){}
  public record QueryParameters(Instant createdOn) {}

  @Query("SELECT * as customers FROM customers_by_creation_time WHERE createdOn >= :createdOn")
  public CustomerList getCustomerByTime(QueryParameters params) {
    return null;
  }

}

