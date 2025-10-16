/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akkajavasdk.components.keyvalueentities.user.ProdCounterEntity;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@akka.javasdk.annotations.Setup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class Bootstrap implements ServiceSetup {

  private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

  @Override
  public void onStartup() {
    logger.info("Starting Application");
  }

  @Override
  public Set<Class<?>> disabledComponents() {
    return Set.of(ProdCounterEntity.class);
  }
}
