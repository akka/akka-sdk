/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.typesafe.config.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ComponentId("test-counter")
public class TestCounterEntity extends KeyValueEntity<Integer> {
  private final String entityId;
  private final Config userConfig;

  public TestCounterEntity(KeyValueEntityContext context, Config userConfig) {
    this.entityId = context.entityId();
    this.userConfig = userConfig;
  }

  @Override
  public Integer emptyState() {
    return 100;
  }

  public Effect<Integer> get() {
    return effects().reply(currentState());
  }

  public Effect<Map<String, String>> getUserConfigKeys(Set<String> keys) {
    var found = new HashMap<String, String>();
    keys.forEach(
        key -> {
          if (userConfig.hasPath(key)) {
            found.put(key, userConfig.getString(key));
          }
        });
    return effects().reply(found);
  }
}
