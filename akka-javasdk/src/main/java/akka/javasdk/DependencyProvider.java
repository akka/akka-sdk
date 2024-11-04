/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/**
 * A factory method to provide additional dependencies to the component implementations.
 * <p>
 * Implementations of this interface must be thread safe.
 */
public interface DependencyProvider {

  /**
   * Get a dependency for a given class. If the dependency is not found, an exception should be thrown.
   * <p>
   * Returned instance for a given class must be safe to use concurrently.
   *
   * @param clazz The class of the dependency to get
   * @return The dependency instance
   * @param <T> The type of the dependency
   */
  <T> T getDependency(Class<T> clazz);

  /**
   * Create a dependency provider that always returns the same instance for a given class.
   * @param single The single instance to return
   * @return The dependency provider
   */
  static DependencyProvider single(Object single){
    return new DependencyProvider() {
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz.isAssignableFrom(single.getClass())) {
          return (T) single;
        } else {
          throw new RuntimeException("No such dependency found: "+ clazz);
        }
      }
    };
  }
}
