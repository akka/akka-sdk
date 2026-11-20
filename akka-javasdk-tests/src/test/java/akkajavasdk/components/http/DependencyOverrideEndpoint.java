/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akkajavasdk.components.OverridableDependency;

/** Returns the name of the injected {@link OverridableDependency}. */
@HttpEndpoint("/dependency-override")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class DependencyOverrideEndpoint {

  private final OverridableDependency dependency;

  public DependencyOverrideEndpoint(OverridableDependency dependency) {
    this.dependency = dependency;
  }

  @Get("/name")
  public String name() {
    return dependency.name();
  }
}
