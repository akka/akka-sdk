/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "service-stream-acl")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
@Produce.ServiceStream(id = "my_stream")
@Acl(allow = @Acl.Matcher(service = "*"))
public class ValidConsumerWithServiceStreamAndAcl extends Consumer {

  public Effect onMessage(Integer message) {
    return effects().done();
  }
}
