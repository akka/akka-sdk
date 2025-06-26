/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akkajavasdk.components.keyvalueentities.user.ProdCounterEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@akka.javasdk.annotations.Setup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Setup implements ServiceSetup {

  private static final Logger logger = LoggerFactory.getLogger(Setup.class);

  @Override
  public void onStartup() {
    logger.info("Starting Application");
  }

  @Override
  public Set<Class<?>> disabledComponents() {
    return Set.of(ProdCounterEntity.class);
  }
}
