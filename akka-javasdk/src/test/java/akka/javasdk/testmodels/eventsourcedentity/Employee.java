/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.eventsourcedentity;

public record Employee(String firstName, String lastName, String email) {

  public Employee withEmail(String newEmail) {
    return new Employee(firstName, lastName, newEmail);
  }
}
