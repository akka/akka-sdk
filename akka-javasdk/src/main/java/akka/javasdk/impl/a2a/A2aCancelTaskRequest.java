/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * INTERNAL API
 *
 * <p>The request of {@code CancelTask}.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param id the id of the task
 * @param metadata free form details about the request
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aCancelTaskRequest(String tenant, String id, Map<String, Object> metadata) {

  public A2aCancelTaskRequest {
    metadata = A2aValues.optionalMap(metadata);
  }
}
