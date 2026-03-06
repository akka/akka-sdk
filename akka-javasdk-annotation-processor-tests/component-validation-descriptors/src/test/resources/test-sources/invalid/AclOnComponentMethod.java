/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

// @Acl on a method of a non-endpoint component - should fail
@Component(id = "acl-on-component-method-entity")
public class AclOnComponentMethod extends KeyValueEntity<String> {

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  public Effect<String> get() {
    return effects().reply(currentState());
  }
}
