/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels;

import java.util.List;

public class NestedMessage {
  public String string;
  public SimpleMessage simpleMessage;

  public InstantWrapper instantWrapper;
  public List<InstantEntryForList> instantsList;
  public InstantEntryForArray[] instantArrays;
}
