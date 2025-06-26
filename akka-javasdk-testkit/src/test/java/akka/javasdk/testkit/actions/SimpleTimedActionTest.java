/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.actions;

import akka.javasdk.testkit.TimedActionResult;
import akka.javasdk.testkit.TimedActionTestkit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimpleTimedActionTest {

  @Test
  public void testEchoCall() {
    TimedActionTestkit<SimpleAction> actionUnitTestkit = TimedActionTestkit.of(SimpleAction::new);
    TimedActionResult result = actionUnitTestkit.call(simpleAction -> simpleAction.echo("Hey"));
    Assertions.assertTrue(result.isDone());
  }
}
