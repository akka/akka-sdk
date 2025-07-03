/**
 * Event Sourced Entity components for building stateful services that persist changes as events in
 * a journal.
 *
 * <p>Event Sourced Entities store a sequence of events rather than the current state directly. The
 * current state is derived by replaying all events from the journal. This approach provides a
 * complete audit trail, enables reliable state replication, and allows for sophisticated
 * event-driven architectures.
 *
 * <p>The main classes in this package are:
 *
 * <ul>
 *   <li>{@link akka.javasdk.eventsourcedentity.EventSourcedEntity} - The base class for
 *       implementing Event Sourced Entities
 *   <li>{@link akka.javasdk.eventsourcedentity.CommandContext} - Context available during command
 *       processing
 *   <li>{@link akka.javasdk.eventsourcedentity.EventContext} - Context available during event
 *       processing
 *   <li>{@link akka.javasdk.eventsourcedentity.EventSourcedEntityContext} - Context available
 *       during entity construction
 * </ul>
 *
 * @see akka.javasdk.eventsourcedentity.EventSourcedEntity
 */
package akka.javasdk.eventsourcedentity;
