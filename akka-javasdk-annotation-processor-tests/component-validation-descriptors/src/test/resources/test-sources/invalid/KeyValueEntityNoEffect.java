/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "kv-entity-no-effect")
public class KeyValueEntityNoEffect extends KeyValueEntity<String> {

  // No Effect method - this should cause a validation error
  public String execute() {
    return "ok";
  }
}
