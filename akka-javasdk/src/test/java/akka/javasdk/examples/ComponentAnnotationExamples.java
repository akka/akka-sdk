/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.examples;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/**
 * Example demonstrating the new Component annotation and backward compatibility with ComponentId.
 */
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

  /**
   * Example using the legacy ComponentId annotation. This continues to work for backward
   * compatibility.
   */
  @Component(id = "legacy-component")
  public static class LegacyEntity extends KeyValueEntity<String> {
    // Component implementation here
  }

  /** Demonstration of how the framework reads component metadata. */
  public static void demonstrateUsage() {
    // Reading metadata from new Component annotation
    System.out.println("New Component annotation:");
    // - ID is available from both new and old annotations
    // - Name and description are only available from the new Component annotation
    // - Framework checks Component first, then falls back to ComponentId for backward compatibility

    System.out.println("Legacy ComponentId annotation:");
    // - Only ID is available
    // - Name and description return empty/null
  }
}
