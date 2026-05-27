package demo.research.application;

import java.util.List;

// tag::class[]
/** Result type for the analyst agent. */
public record AnalysisReport(String topic, String assessment, List<String> trends) {}
// end::class[]
