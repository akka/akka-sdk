/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akkajavasdk.components.OtherDependency;
import akkajavasdk.components.OverridableDependency;

/** Returns the names of the injected {@link OverridableDependency} and {@link OtherDependency}. */
@HttpEndpoint("/dependency-override")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class DependencyOverrideEndpoint {

  private final OverridableDependency dependency;
  private final OtherDependency otherDependency;

  public DependencyOverrideEndpoint(
      OverridableDependency dependency, OtherDependency otherDependency) {
    this.dependency = dependency;
    this.otherDependency = otherDependency;
  }

  @Get("/name")
  public String name() {
    return dependency.name();
  }

  @Get("/other-name")
  public String otherName() {
    return otherDependency.name();
  }
}
