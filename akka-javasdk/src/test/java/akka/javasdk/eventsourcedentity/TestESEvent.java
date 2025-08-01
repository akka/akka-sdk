/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.annotations.Migration;
import akka.javasdk.annotations.TypeName;

public sealed interface TestESEvent {

  @Migration(Event1Migration.class)
  record Event1(String s) implements TestESEvent {}

  @Migration(Event2Migration.class)
  record Event2(int newName) implements TestESEvent {}

  @TypeName("old-event-3")
  record Event3(boolean b) implements OldTestESEvent, TestESEvent {}

  @Migration(Event4Migration.class)
  record Event4(String anotherString) implements OldTestESEvent, TestESEvent {}
}
