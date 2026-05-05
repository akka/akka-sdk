package demo.research.application;

import java.util.List;

// tag::class[]
/** Result type for the researcher agent. */
public record ResearchFindings(String topic, List<String> facts, List<String> sources) {}
// end::class[]
