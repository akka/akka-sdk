/**
 * Brainstorm — emergent coordination via shared environment.
 *
 * <p>Multiple instances of a simple Ideator agent operate in parallel on a shared IdeaBoardEntity.
 * Agents don't communicate directly — they influence each other indirectly by reading what others
 * have contributed and building on it (stigmergy). Later agents see and refine earlier
 * contributions. When all ideators complete, a Curator agent reads the final board state and
 * selects the best ideas.
 *
 * <p>This demonstrates the emergent coordination pattern: many simple agents, indirect
 * communication through a shared environment, and external selection. No agent-to-agent
 * coordination capabilities are used — all interaction flows through the shared IdeaBoardEntity via
 * domain tools that use {@code ComponentClient} for entity access.
 *
 * <p><b>Capabilities demonstrated:</b> Emergent coordination (stigmergy), shared environment
 * (event-sourced entity), ComponentClient-enabled domain tools, external selection (curator).
 *
 * <p><b>Agents:</b> Ideator (contributes/refines/rates ideas) and Curator (selects best ideas),
 * orchestrated by BrainstormEndpoint.
 *
 * <p><b>See:</b> {@code http/brainstorm.http} for example requests.
 */
package demo.brainstorm;
