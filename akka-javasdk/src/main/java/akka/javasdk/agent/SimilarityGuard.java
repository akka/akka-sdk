/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.japi.Util;
import akka.runtime.sdk.spi.SpiAgent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SimilarityGuard extends SpiAgent.SimilarityGuard {
  private static final List<String> DEFAULT_JAILBREAK_RESOURCES;

  static {
    var jailbreakList = new ArrayList<String>();
    for (int i = 1; i <= 10; i++) {
      jailbreakList.add("guardrail/jailbreak/prompt-" + i + ".txt");
    }
    DEFAULT_JAILBREAK_RESOURCES = Collections.unmodifiableList(jailbreakList);
  }

  public static SimilarityGuard defaultJailbreakGuard() {
    return new SimilarityGuard(Guardrail.Category.JAILBREAK, DEFAULT_JAILBREAK_RESOURCES);
  }

  public SimilarityGuard(Guardrail.Category category, List<String> badExamplesResources) {
    super(category.name().toLowerCase(Locale.ROOT), false, Util.immutableSeq(badExamplesResources));
  }

  public SimilarityGuard(String otherCategory, List<String> badExamplesResources) {
    super(otherCategory, false, Util.immutableSeq(badExamplesResources));
  }
}
