/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@akka.javasdk.annotations.Setup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Setup implements ServiceSetup {

  private static final Logger logger = LoggerFactory.getLogger(Setup.class);

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  // optional injected stuff
  public Setup(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }
}
