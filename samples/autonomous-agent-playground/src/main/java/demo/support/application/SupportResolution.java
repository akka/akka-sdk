package demo.support.application;

import java.util.List;

/** Typed result for a support ticket resolution. */
public record SupportResolution(
  String resolution,
  String category,
  List<String> agentChain
) {}
