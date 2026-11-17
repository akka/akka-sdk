/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "cart-consumer")
@Consume.FromTopic("carts")
public class CartConsumer extends Consumer {

  public Effect onEvent(String event) {
    return effects().done();
  }
}
