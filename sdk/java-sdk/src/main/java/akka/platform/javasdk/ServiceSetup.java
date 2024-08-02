/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk;

public interface ServiceSetup {

  default ServiceLifecycle serviceLifecycle() {
    return null;
  }

  /** Invoked once when the service is starting to create a dependency provider. */
  default DependencyProvider createDependencyProvider() {
    return null;
  }
}
