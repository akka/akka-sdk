/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import java.util.concurrent.ConcurrentHashMap;

public class DummyTransferStore {

  private static ConcurrentHashMap<String, TransferState> customers = new ConcurrentHashMap<>();

  public static void store(String storeName, String workflowId, TransferState state) {
    customers.put(storeName + "-" + workflowId, state);
  }

  public static void delete(String storeName, String workflowId) {
    customers.remove(storeName + "-" + workflowId);
  }

  public static TransferState get(String storeName, String workflowId) {
    return customers.get(storeName + "-" + workflowId);
  }
}
