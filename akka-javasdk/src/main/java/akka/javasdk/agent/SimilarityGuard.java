/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class SimilarityGuard implements Guardrail {
  private static final List<String> DEFAULT_JAILBREAK_RESOURCES;
  private static final double DefaultThreshold = 0.75;

  static {
    var jailbreakList = new ArrayList<String>();
    for (int i = 1; i <= 10; i++) {
      jailbreakList.add("guardrail/jailbreak/prompt-" + i + ".txt");
    }
    DEFAULT_JAILBREAK_RESOURCES = Collections.unmodifiableList(jailbreakList);
  }

  public static SimilarityGuard defaultJailbreakGuard() {
    return new SimilarityGuard(
        "jailbreak guard", Guardrail.Category.JAILBREAK, DEFAULT_JAILBREAK_RESOURCES);
  }

  private final String name;
  private final Category category;
  private final Optional<String> otherCategory;
  private final List<String> badExamplesResources;
  private final double threshold;
  private final boolean reportOnly;

  private SimilarityGuard(
      String name,
      Category category,
      Optional<String> otherCategory,
      List<String> badExamplesResources,
      double threshold,
      boolean reportOnly) {
    this.name = name;
    this.category = category;
    this.otherCategory = otherCategory;
    this.badExamplesResources = Collections.unmodifiableList(new ArrayList<>(badExamplesResources));
    this.threshold = threshold;
    this.reportOnly = false;
  }

  public SimilarityGuard(
      String name, Guardrail.Category category, List<String> badExamplesResources) {
    this(name, category, Optional.empty(), badExamplesResources, DefaultThreshold, false);
  }

  public SimilarityGuard(String name, String otherCategory, List<String> badExamplesResources) {
    this(
        name,
        Category.OTHER,
        Optional.of(otherCategory),
        badExamplesResources,
        DefaultThreshold,
        false);
  }

  public SimilarityGuard withThreshold(double threshold) {
    return new SimilarityGuard(
        name, category, otherCategory, badExamplesResources, threshold, reportOnly);
  }

  public SimilarityGuard withReportOnly() {
    return new SimilarityGuard(
        name, category, otherCategory, badExamplesResources, threshold, true);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Category category() {
    return category;
  }

  @Override
  public String otherCategory() {
    if (category.equals(Category.OTHER)) return otherCategory.get();
    else return null;
  }

  public double threshold() {
    return threshold;
  }

  public boolean reportOnly() {
    return reportOnly;
  }

  public List<String> badExamplesResources() {
    return badExamplesResources;
  }

  @Override
  public Result evaluate(String text) {
    throw new IllegalStateException("Not expected to be called");
  }
}
