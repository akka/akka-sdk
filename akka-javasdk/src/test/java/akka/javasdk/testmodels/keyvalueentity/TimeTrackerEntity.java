/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ComponentId("timer")
public class TimeTrackerEntity extends KeyValueEntity<TimeTrackerEntity.TimerState> {


  public static class TimerState {

    final public String name;
    final public Instant createdTime;
    final public List<TimerEntry> entries;

    public TimerState(String name, Instant createdTime, List<TimerEntry> entries) {
      this.name = name;
      this.createdTime = createdTime;
      this.entries = entries;
    }
  }

  public static class TimerEntry {
    final public Instant started;
    final public Instant stopped = Instant.MAX;

    public TimerEntry(Instant started) {
      this.started = started;
    }
  }

  public Effect<String> start(String timerId) {
    if (currentState() == null)
      return effects().updateState(new TimerState(timerId, Instant.now(), new ArrayList<>())).thenReply("Created");
    else
      return effects().error("Already created");
  }
}
