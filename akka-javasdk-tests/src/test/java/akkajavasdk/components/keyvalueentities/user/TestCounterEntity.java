/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.NotificationPublisher;
import akka.javasdk.NotificationPublisher.NotificationStream;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.typesafe.config.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component(id = "test-counter")
public class TestCounterEntity extends KeyValueEntity<Integer> {
  private final String entityId;
  private final Config userConfig;
  private final NotificationPublisher<String> notificationPublisher;

  public TestCounterEntity(
      KeyValueEntityContext context,
      Config userConfig,
      NotificationPublisher<String> notificationPublisher) {
    this.entityId = context.entityId();
    this.userConfig = userConfig;
    this.notificationPublisher = notificationPublisher;
  }

  @Override
  public Integer emptyState() {
    return 100;
  }

  public Effect<Integer> get() {
    return effects().reply(currentState());
  }

  public Effect<Integer> increase(int value) {
    int newValue = currentState() + value;
    return effects()
        .updateState(newValue)
        .thenReply(
            () -> {
              notificationPublisher.publish("counter set to " + newValue);
              return newValue;
            });
  }

  public NotificationStream<String> updates() {
    return notificationPublisher.stream();
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
