/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@akka.javasdk.annotations.Setup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Setup implements ServiceSetup {

  private static final Logger logger = LoggerFactory.getLogger(Setup.class);

  @Override
  public void onStartup() {
    logger.info("Starting Application");
  }

}
