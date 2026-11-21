/**
 * Data types of the Agent2Agent (A2A) protocol, version 1.0.
 *
 * <p>The records and enums in this package follow the protocol's JSON form (the Protocol Buffers
 * JSON mapping of the A2A definitions). Field names are camelCase, enums use their protocol names
 * such as {@code TASK_STATE_COMPLETED}, and timestamps are ISO 8601 strings in UTC. Serialize them
 * with {@link akka.javasdk.JsonSupport#getObjectMapper()}.
 *
 * <p>Fields that the protocol marks as required are not enforced on read, because agents in the
 * field omit some of them. The exception is a union, such as {@link akka.javasdk.a2a.A2aPart},
 * which must have exactly one member set.
 */
package akka.javasdk.a2a;
