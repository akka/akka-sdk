/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.annotations.TypeName;

public interface OldTestESEvent {

  record OldEvent1(String s) implements OldTestESEvent {}

  record OldEvent2(int i) implements OldTestESEvent {}

  @TypeName("old-event-3")
  record OldEvent3(boolean b) implements OldTestESEvent {}

  @TypeName("old-event-5")
  record OldEvent5(String value) implements OldTestESEvent {}
}
