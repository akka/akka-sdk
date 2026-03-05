package demo.research.application;

import java.util.List;

/** Result type for the researcher agent. */
public record ResearchFindings(String topic, List<String> facts, List<String> sources) {}
