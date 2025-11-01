package com.example;

import akka.javasdk.keyvalueentity.KeyValueEntity;

public class SimpleKeyValueEntity extends KeyValueEntity<Integer> {
  @Override
  public Integer emptyState() {
    return 0;
  }
}
