/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

public class CreateUser {

  public final String firstName;
  public final String lastName;

  public CreateUser(String firstName, String lastName) {
    this.firstName = firstName;
    this.lastName = lastName;
  }
}
