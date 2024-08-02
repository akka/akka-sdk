/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import akka.platform.javasdk.ServiceSetup;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.PlatformServiceSetup;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PlatformServiceSetup
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
