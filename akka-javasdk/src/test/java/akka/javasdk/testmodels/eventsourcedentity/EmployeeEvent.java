/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.eventsourcedentity;

import akka.javasdk.annotations.Migration;
import akka.javasdk.annotations.TypeName;

public sealed interface EmployeeEvent {

  @TypeName("created")
  @Migration(EmployeeCreatedMigration.class)
  final class EmployeeCreated implements EmployeeEvent {

    public final String firstName;
    public final String lastName;
    public final String email;

    public EmployeeCreated(String firstName, String lastName, String email) {
      this.firstName = firstName;
      this.lastName = lastName;
      this.email = email;
    }
  }

  @TypeName("emailUpdated")
  final class EmployeeEmailUpdated implements EmployeeEvent {

    public final String email;

    public EmployeeEmailUpdated(String email) {
      this.email = email;
    }
  }

}
