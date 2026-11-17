/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "span-recording-view")
public class SpanRecordingView extends View {

  public record Row(int count) {}

  @Consume.FromEventSourcedEntity(SpanRecordingEntity.class)
  public static class SpanRows extends TableUpdater<Row> {

    public Effect<Row> onEvent(SpanRecordingEntity.Event event) {
      SpanRecorder.record("view:" + updateContext().eventSubject().get());
      return effects().updateRow(new Row(((SpanRecordingEntity.Event.Recorded) event).count()));
    }
  }

  @Query("SELECT * FROM span_rows")
  public QueryEffect<Row> getAny() {
    return queryResult();
  }
}
