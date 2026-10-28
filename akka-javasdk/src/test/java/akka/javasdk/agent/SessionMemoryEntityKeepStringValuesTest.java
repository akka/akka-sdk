/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import static akka.javasdk.agent.SessionMemoryEntity.keepStringValues;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SessionMemoryEntityKeepStringValuesTest {

  @Test
  public void keepsStringAndStringListAttributes() {
    var image = new Object();
    var attributes = new HashMap<String, Object>();
    attributes.put("signature", "abc");
    attributes.put("redacted_thinking", List.of("r1", "r2"));
    attributes.put("empty", List.of());
    attributes.put("mixed", List.of("a", image));
    attributes.put("image", image);
    attributes.put("absent", null);

    var kept = keepStringValues(attributes);

    assertThat(kept)
        .isEqualTo(
            Map.of(
                "signature", "abc",
                "redacted_thinking", List.of("r1", "r2"),
                "empty", List.of()));
  }

  @Test
  public void dropsAListWithANullElement() {
    var kept = keepStringValues(Map.of("redacted_thinking", Arrays.asList("r1", null)));

    assertThat(kept).isEmpty();
  }

  @Test
  public void dropsANullKey() {
    var attributes = new HashMap<String, Object>();
    attributes.put(null, "value");

    var kept = keepStringValues(attributes);

    assertThat(kept).isEmpty();
  }

  @Test
  public void givesAnEmptyMapForNullAttributes() {
    var kept = keepStringValues(null);

    assertThat(kept).isEmpty();
  }

  @Test
  public void isNotChangedByTheSourceMap() {
    var attributes = new HashMap<String, Object>();
    attributes.put("signature", "abc");

    var kept = keepStringValues(attributes);
    attributes.put("added", "later");

    assertThat(kept).doesNotContainKey("added");
  }

  @Test
  public void isNotChangedByTheSourceList() {
    var redactedThinking = new ArrayList<String>();
    redactedThinking.add("r1");

    var kept = keepStringValues(Map.of("redacted_thinking", redactedThinking));
    redactedThinking.add("r2");

    assertThat(kept).containsEntry("redacted_thinking", List.of("r1"));
  }
}
