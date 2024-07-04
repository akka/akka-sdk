package com.example.callanotherservice;

import akka.platform.javasdk.ServiceLifecycle;
import akka.platform.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @KalixService (only one per service)
// Allow all other Kalix services deployed in the same project to access the components of this
// Kalix service, but disallow access from the internet. This can be overridden explicitly
// per component or method using annotations.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(service = "*"))
public class Main implements ServiceLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  @Override
  public void onStartup() {
    logger.info("Starting Kalix Application");
  }

}