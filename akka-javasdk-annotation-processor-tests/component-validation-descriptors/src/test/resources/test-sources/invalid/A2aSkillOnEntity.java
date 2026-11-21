/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "skill-entity")
@A2aSkill(id = "store")
public class A2aSkillOnEntity extends KeyValueEntity<String> {
  public Effect<String> execute(String command) {
    return effects().reply(command);
  }
}
