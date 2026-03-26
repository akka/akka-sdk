/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.task.Task;

public class SimpleTaskDefs {

  public static final Task<SimpleResult> ANSWER =
      Task.define("Answer").description("Answer a question").resultConformsTo(SimpleResult.class);
}
