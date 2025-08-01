/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.pubsub;

import akkajavasdk.components.keyvalueentities.customer.CustomerEntity.Customer;
import java.util.concurrent.ConcurrentHashMap;

public class DummyCustomerStore {

  private static ConcurrentHashMap<String, Customer> customers = new ConcurrentHashMap<>();

  public static void store(String storeName, String entityId, Customer customer) {
    customers.put(storeName + "-" + entityId, customer);
  }

  public static Customer get(String storeName, String entityId) {
    return customers.get(storeName + "-" + entityId);
  }
}
