/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.eventsourcedentity;

public record Employee(String firstName, String lastName, String email) {

  public Employee withEmail(String newEmail) {
    return new Employee(firstName, lastName, newEmail);
  }
}
