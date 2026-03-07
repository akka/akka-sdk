package demo.research.application;

import java.util.List;

/** Typed result for a research brief — produced by the coordinator after synthesising findings. */
public record ResearchBrief(String title, String summary, List<String> keyFindings) {}
