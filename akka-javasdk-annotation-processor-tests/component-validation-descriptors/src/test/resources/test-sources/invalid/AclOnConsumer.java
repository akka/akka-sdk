/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

// @Acl on a Consumer without @Produce.ServiceStream - should fail
@Component(id = "acl-on-consumer")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
@Acl(allow = @Acl.Matcher(service = "*"))
public class AclOnConsumer extends Consumer {

  public Effect onMessage(Integer message) {
    return effects().done();
  }
}
