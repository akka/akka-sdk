/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

// @Acl on a non-endpoint component (KeyValueEntity) - should fail
@Component(id = "acl-on-component-entity")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AclOnComponent extends KeyValueEntity<String> {

  public Effect<String> get() {
    return effects().reply(currentState());
  }
}
