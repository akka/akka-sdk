/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package spring.testmodels.actions;

import akka.platform.javasdk.testkit.ActionResult;
import akka.platform.javasdk.testkit.ActionTestkit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimpleActionTest {

  @Test
  public void testEchoCall() {
    ActionTestkit<SimpleAction> actionUnitTestkit = ActionTestkit.of(SimpleAction::new);
    ActionResult result = actionUnitTestkit.call(simpleAction -> simpleAction.echo("Hey"));
    Assertions.assertTrue(result.isDone());
  }
}
