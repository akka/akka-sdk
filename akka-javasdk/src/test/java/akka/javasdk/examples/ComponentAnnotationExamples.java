/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.examples;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/** Example demonstrating the Component annotation. */
public class ComponentAnnotationExamples {

  /**
   * Example using the new Component annotation with all fields. This is the recommended approach
   * going forward.
   */
  @Component(
      id = "user-profile",
      name = "User Profile Entity",
      description = "Manages user profile information including preferences and settings")
  public static class UserProfileEntity extends KeyValueEntity<String> {
    // Component implementation here
  }

  /** Example using the new Component annotation with only the required id field. */
  @Component(id = "simple-counter")
  public static class SimpleCounterEntity extends KeyValueEntity<Integer> {
    // Component implementation here
  }

  /** Demonstration of how the framework reads component metadata. */
  public static void demonstrateUsage() {
    // Reading metadata from Component annotation
    System.out.println("Component annotation:");
    // - ID is available from the annotation
    // - Name and description are optional fields
  }
}
