/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component(id = "timer")
public class TimeTrackerEntity extends KeyValueEntity<TimeTrackerEntity.TimerState> {

  public static class TimerState {

    public final String name;
    public final Instant createdTime;
    public final List<TimerEntry> entries;

    public TimerState(String name, Instant createdTime, List<TimerEntry> entries) {
      this.name = name;
      this.createdTime = createdTime;
      this.entries = entries;
    }
  }

  public static class TimerEntry {
    public final Instant started;
    public final Instant stopped = Instant.MAX;

    public TimerEntry(Instant started) {
      this.started = started;
    }
  }

  public Effect<String> start(String timerId) {
    if (currentState() == null)
      return effects()
          .updateState(new TimerState(timerId, Instant.now(), new ArrayList<>()))
          .thenReply("Created");
    else return effects().error("Already created");
  }
}
