/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A mock of a remote A2A agent for tests. Register it with {@link
 * TestKit.Settings#withMockedA2aAgent(MockA2aAgent)}.
 *
 * <p>The testkit serves an agent card with the skills of the mock and answers calls to them. It
 * also sets the configuration of the agent name, so {@code RemoteA2aAgent.named(name)} in the
 * service under test reaches the mock, without any other configuration.
 *
 * <p>A skill handler receives the text of the message from the agent under test and returns the
 * text of the reply. When the handler throws, the remote agent reports a failed task, which the
 * agent under test sees as an error result. Handlers can run concurrently, so state that they share
 * with the test must be thread-safe.
 */
public final class MockA2aAgent {

  /** A skill of the mock. */
  public record Skill(String id, String description, Function<String, String> handler) {}

  private final String name;
  private final List<Skill> skills;

  private MockA2aAgent(String name, List<Skill> skills) {
    this.name = name;
    this.skills = skills;
  }

  /**
   * @param name The logical name of the remote agent, as in {@code RemoteA2aAgent.named(name)}.
   */
  public static MockA2aAgent named(String name) {
    if (name == null || !name.matches("[A-Za-z0-9._-]+"))
      throw new IllegalArgumentException(
          "name must only have letters, digits, '.', '_' and '-', but was [" + name + "]");
    return new MockA2aAgent(name, List.of());
  }

  /**
   * Add a skill to the mock. The model sees it as the tool {@code <name>__<id>}.
   *
   * @param id The skill id.
   * @param description The description of the skill, shown to the model.
   * @param handler Maps the text of the message to the text of the reply.
   */
  public MockA2aAgent withSkill(String id, String description, Function<String, String> handler) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be empty");
    var updated = new ArrayList<>(skills);
    updated.add(new Skill(id, description, handler));
    return new MockA2aAgent(name, Collections.unmodifiableList(updated));
  }

  /** The logical name of the remote agent. */
  public String name() {
    return name;
  }

  /** The skills of the mock. */
  public List<Skill> skills() {
    return skills;
  }
}
