/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

record TestUser(String id, String email, String name) {

  public TestUser withName(String newName) {
    return new TestUser(id, email, newName);
  }

  public TestUser withEmail(String newEmail) {
    return new TestUser(id, newEmail, name);
  }
}
