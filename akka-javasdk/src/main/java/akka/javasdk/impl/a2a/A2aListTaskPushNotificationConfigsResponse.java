/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * INTERNAL API
 *
 * <p>The result of {@code ListTaskPushNotificationConfigs}.
 *
 * @param configs the configurations of this page
 * @param nextPageToken the token for the next page, empty on the last page
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aListTaskPushNotificationConfigsResponse(
    List<A2aTaskPushNotificationConfig> configs, String nextPageToken) {

  public A2aListTaskPushNotificationConfigsResponse {
    configs = A2aValues.list(configs);
  }
}
