/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package spring.testmodels.actions;

import akka.platform.javasdk.testkit.TimedActionResult;
import akka.platform.javasdk.testkit.TimedActionTestkit;
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
