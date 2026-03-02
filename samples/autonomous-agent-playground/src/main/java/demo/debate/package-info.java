/**
 * Structured Debate — team with messaging-driven collaboration.
 *
 * <p>A moderator forms a team of debaters and assigns each a position. Unlike the devteam sample
 * where agents work independently from a shared task list, here the collaboration IS the
 * conversation — debaters use {@code sendMessage} to challenge each other's arguments, respond to
 * critiques, and refine positions. The moderator monitors the exchange and synthesises the outcome.
 *
 * <p><b>Capabilities demonstrated:</b> Team formation, peer-to-peer messaging (sendMessage),
 * messaging-driven collaboration (as opposed to task-list-driven work).
 *
 * <p><b>Agents:</b> DebateModerator (forms team, synthesises) → Debater (team member, argues and
 * responds via messaging).
 *
 * <p><b>See:</b> {@code http/debate.http} for example requests.
 */
package demo.debate;
