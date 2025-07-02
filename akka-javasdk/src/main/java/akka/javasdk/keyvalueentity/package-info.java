/**
 * Key Value Entity components for building stateful services that persist complete state on every change.
 * 
 * <p>Key Value Entities are stateful components that store their entire state with each update,
 * unlike Event Sourced Entities which store a sequence of events. This makes them suitable for
 * use cases where you need simple state management without event history.
 * 
 * <p>The main classes in this package are:
 * <ul>
 *   <li>{@link akka.javasdk.keyvalueentity.KeyValueEntity} - The base class for implementing Key Value Entities</li>
 *   <li>{@link akka.javasdk.keyvalueentity.CommandContext} - Context available during command processing</li>
 *   <li>{@link akka.javasdk.keyvalueentity.KeyValueEntityContext} - Context available during entity construction</li>
 * </ul>
 * 
 * @see akka.javasdk.keyvalueentity.KeyValueEntity
 */
package akka.javasdk.keyvalueentity;
