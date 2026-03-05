package demo.research.application;

import java.util.List;

/** Typed result for research brief tasks. */
public record ResearchBrief(String title, String summary, List<String> keyFindings) {}
