package demo.docreview.application;

import java.util.List;

/** Typed result for document review tasks. */
public record ReviewResult(String assessment, List<String> findings, boolean compliant) {}
