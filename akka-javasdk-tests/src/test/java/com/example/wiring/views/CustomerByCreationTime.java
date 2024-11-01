/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import akka.javasdk.view.TableUpdater;
import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

import java.time.Instant;
import java.util.List;


@ComponentId("view_customers_by_creation_time")
public class CustomerByCreationTime extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<CustomerEntity.Customer> {}

  public record CustomerList(List<CustomerEntity.Customer> customers){}
  public record QueryParameters(Instant createdOn) {}

  @Query("SELECT * as customers FROM customers WHERE createdOn >= :createdOn")
  public QueryEffect<CustomerList> getCustomerByTime(QueryParameters params) {
    return queryResult();
  }

}

