/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

public interface ShopGrpcService {
  String getItem(String id);

  String listItems(String filter);

  String removeItem(String id);

  default String ping() {
    return "pong";
  }

  static String version() {
    return "1";
  }

  static String name = "com.example.inventory.ShopService";
}
