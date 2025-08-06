/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.keyvalueentity;

import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testkit.eventsourced.Response;
import java.util.List;

public class CounterValueEntity extends KeyValueEntity<Integer> {

  public record SomeRecord(String text) {}

  @Override
  public Integer emptyState() {
    return 0;
  }

  public Effect<String> increaseBy(Integer value) {
    Integer state = currentState();
    if (value < 0) return effects().error("Can't increase with a negative value");
    else return effects().updateState(state + value).thenReply("Ok");
  }

  public Effect<String> increaseFromMeta() {
    Integer state = currentState();
    return effects()
        .updateState(state + Integer.parseInt(commandContext().metadata().get("value").get()))
        .thenReply("Ok");
  }

  public Effect<String> delete() {
    return effects().deleteEntity().thenReply("Deleted");
  }

  public Effect<Response> polyResponse() {
    return effects().reply(new Response.Error());
  }

  public Effect<String> polyHandler(Response response) {
    return effects().reply("");
  }

  public ReadOnlyEffect<List<SomeRecord>> returnList() {
    return effects().reply(List.of(new SomeRecord("ok")));
  }
}
