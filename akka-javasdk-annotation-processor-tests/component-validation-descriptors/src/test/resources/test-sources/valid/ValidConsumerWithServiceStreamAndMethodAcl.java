/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "service-stream-method-acl")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
@Produce.ServiceStream(id = "my_stream")
public class ValidConsumerWithServiceStreamAndMethodAcl extends Consumer {

  @Acl(allow = @Acl.Matcher(service = "some-service"))
  public Effect onMessage(Integer message) {
    return effects().done();
  }
}
