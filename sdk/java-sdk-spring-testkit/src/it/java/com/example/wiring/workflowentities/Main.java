/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import kalix.javasdk.ServiceLifecycle;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.KalixService;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KalixService
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Main implements ServiceLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  // optional injected stuff
  public Main(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }

  public static void main(String[] args) {
    logger.info("Starting Kalix - Spring Workflows Tests");
  }
}
