/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.task.Task;

public class DelegationTaskDefs {

  public record ResearchResult(String title, String summary) {}

  public record FindingsResult(String topic, String findings) {}

  public record SupportResolution(String category, String resolution, boolean resolved) {}

  public static final Task<ResearchResult> RESEARCH =
      Task.define("Research")
          .description("Produce a research summary")
          .resultConformsTo(ResearchResult.class);

  public static final Task<FindingsResult> FINDINGS =
      Task.define("Findings")
          .description("Research a topic and produce findings")
          .resultConformsTo(FindingsResult.class);

  public static final Task<SupportResolution> RESOLVE =
      Task.define("Resolve")
          .description("Resolve a support request")
          .resultConformsTo(SupportResolution.class);
}
