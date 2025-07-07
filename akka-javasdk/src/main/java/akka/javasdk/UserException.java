/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.workflow.Workflow;

/**
 * An exception that can be thrown by user code to signal domain validation errors or business rule violations.
 * 
 * <p>This exception is designed to be used in command handlers of {@link KeyValueEntity}, {@link EventSourcedEntity}, 
 * or {@link Workflow} components when the incoming command doesn't fulfill the requirements or the current state 
 * doesn't allow the command to be handled.
 * 
 * <p><strong>HTTP Response Behavior:</strong>
 * <ul>
 *   <li>By default, {@code UserException} is transformed into an HTTP 400 Bad Request response</li>
 *   <li>The exception message becomes the response body</li>
 *   <li>Can be caught and transformed into custom HTTP responses for fine-tuned error handling</li>
 * </ul>
 * 
 * <p><strong>Network Serialization:</strong>
 * Only {@code UserException} and its subtypes are serialized and sent over the network when components 
 * are called across different nodes. Other exceptions are transformed into generic HTTP 500 errors.
 * The Jackson serialization is configured to ignore fields like stack trace or cause from the 
 * {@link Throwable} class.
 * 
 * <p><strong>Usage Examples:</strong>
 * 
 * <p>Using error effects:
 * <pre>{@code
 * // In a command handler
 * if (value > 10000) {
 *   return effects().error("Increasing counter above 10000 is blocked");
 * }
 * }</pre>
 * 
 * <p>Throwing directly:
 * <pre>{@code
 * // In a command handler
 * if (value > 10000) {
 *   throw new UserException("Increasing counter above 10000 is blocked");
 * }
 * }</pre>
 * 
 * <p>Creating custom subtypes:
 * <pre>{@code
 * public class CounterLimitExceededException extends UserException {
 *   public CounterLimitExceededException(String message) {
 *     super(message);
 *   }
 * }
 * 
 * // Usage
 * throw new CounterLimitExceededException("Counter limit exceeded");
 * }</pre>
 * 
 * <p><strong>Error Handling in Endpoints:</strong>
 * <pre>{@code
 * try {
 *   return componentClient
 *            .forEventSourcedEntity(counterId)
 *            .method(CounterEntity::increase)
 *            .invoke(value);
 * } catch (UserException e) {
 *   // Handle the user exception, e.g., return a bad request response
 * }
 * }</pre>
 * 
 * @see KeyValueEntity
 * @see EventSourcedEntity  
 * @see Workflow
 */
public class UserException extends IllegalArgumentException {

  public UserException(String message) {
    super(message);
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
