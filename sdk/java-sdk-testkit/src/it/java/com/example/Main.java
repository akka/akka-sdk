/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.platform.javasdk.ServiceLifecycle;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.KalixService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KalixService
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Main implements ServiceLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  @Override
  public void onStartup() {
    logger.info("Starting Kalix Application");
  }

}
