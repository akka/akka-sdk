/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.annotations.TypeName;

public interface OldTestESEvent {

  record OldEvent1(String s) implements OldTestESEvent {}

  record OldEvent2(int i) implements OldTestESEvent {}

  @TypeName("old-event-3")
  record OldEvent3(boolean b) implements OldTestESEvent {}
}
