package demo.research.application;

import java.util.List;

/** Result type for the analyst agent. */
public record AnalysisReport(String topic, String assessment, List<String> trends) {}
