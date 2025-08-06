/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.view;

import akka.javasdk.testmodels.keyvalueentity.CounterState;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Collection;

public class EmployeeCounters {

  public final String firstName;
  public final String lastName;
  public final String email;
  public final Collection<CounterState> counters;

  @JsonCreator
  public EmployeeCounters(
      String firstName, String lastName, String email, Collection<CounterState> counters) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.counters = counters;
  }
}
