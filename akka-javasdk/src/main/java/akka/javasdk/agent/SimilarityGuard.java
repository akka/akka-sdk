/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Collections;
import java.util.List;

public final class SimilarityGuard implements Guardrail {
  private final Guardrail.Category category;
  private final String otherCcategory;
  private final List<String> badExamplesResources;

  public static SimilarityGuard defaultJailbreakPrompts() {
    return new SimilarityGuard(Category.JAILBREAK, Collections.emptyList());
  }

  public SimilarityGuard(Category category, List<String> badExamplesResources) {
    this.category = category;
    this.otherCcategory = null;
    this.badExamplesResources = badExamplesResources;
  }

  public SimilarityGuard(String otherCategory, List<String> badExamplesResources) {
    this.category = Category.OTHER;
    this.otherCcategory = otherCategory;
    this.badExamplesResources = badExamplesResources;
  }

  @Override
  public Result evaluate(String text) {
    // this is implemented by the runtime
    return null;
  }

  @Override
  public Category category() {
    return this.category;
  }

  @Override
  public String otherCategory() {
    return this.otherCcategory;
  }

  public List<String> getBadExamplesResources() {
    return badExamplesResources;
  }
}
