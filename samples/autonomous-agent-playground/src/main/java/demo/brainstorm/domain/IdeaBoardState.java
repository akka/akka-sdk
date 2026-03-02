package demo.brainstorm.domain;

import java.util.List;

/** State of the shared idea board — the environment for emergent coordination. */
public record IdeaBoardState(String topic, List<Idea> ideas) {
  public record Idea(
    String id,
    String text,
    String contributor,
    int rating,
    String refinement
  ) {}

  public static IdeaBoardState empty() {
    return new IdeaBoardState("", List.of());
  }
}
