/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.KalixService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

@KalixService
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    logger.info("Starting Kalix - Spring Workflows Tests");
    SpringApplication.run(Main.class, args);
  }
}
