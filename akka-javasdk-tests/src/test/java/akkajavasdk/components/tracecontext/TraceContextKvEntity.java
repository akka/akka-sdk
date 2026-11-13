/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "trace-context-kv-entity")
public class TraceContextKvEntity extends KeyValueEntity<String> {

  public Effect<SpanIds> observe() {
    var entityId = commandContext().entityId();
    return effects().updateState(entityId).thenReply(SpanIds.observe(commandContext().tracing()));
  }
}
