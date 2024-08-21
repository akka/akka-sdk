/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.platform.javasdk.ServiceSetup;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.PlatformServiceSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PlatformServiceSetup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Setup implements ServiceSetup {

  private static final Logger logger = LoggerFactory.getLogger(Setup.class);

  @Override
  public void onStartup() {
    logger.info("Starting Application");
  }

}
