/*
 * Copyright Lightbend Inc.
 */

package com.example;

import akka.javasdk.annotations.Setup;
import akka.javasdk.ServiceSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service setup discovered by the SDK runner on the user classloader. Logs a marker on startup
 * through the {@code com.example.user-marker} logger, whose level is raised to DEBUG by the user's
 * logback include — the IT asserts both that the level took effect (logback include was processed
 * across the classloader boundary) and that startup completed.
 */
@Setup
public class Bootstrap implements ServiceSetup {

  private static final Logger log = LoggerFactory.getLogger("com.example.user-marker");

  @Override
  public void onStartup() {
    log.debug("user-marker logger active; isolated-boot service started");
  }
}
