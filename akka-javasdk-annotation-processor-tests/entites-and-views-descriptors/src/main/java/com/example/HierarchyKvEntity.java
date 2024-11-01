/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.ComponentId;

@ComponentId("hierarchy-kv-entity")
public class HierarchyKvEntity extends AbstractInbetweenKvEntity2<AbstractInbetweenEsEntity.State> {
  public record State(String value) {}

}
