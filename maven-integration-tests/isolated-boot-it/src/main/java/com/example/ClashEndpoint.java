/*
 * Copyright Lightbend Inc.
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;

/**
 * User component for the isolated-boot integration test. Everything this endpoint does is observed
 * from the <em>user</em> classloader, which is the whole point: the assertions in {@code
 * IsolatedBootIT} use it to prove the classpath partition is real.
 */
@HttpEndpoint("/clash")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ClashEndpoint extends AbstractHttpEndpoint {

  @Get("/hello")
  public String hello() {
    return "Hello World";
  }

  /**
   * The version of the clashing library as seen from user code. Resolves the {@code Implementation-Version}
   * from the manifest of the jar that actually loaded the class — under isolation this must be the
   * user-declared version, not the copy the runtime ships internally.
   */
  @Get("/lib-version")
  public String libVersion() {
    return ClashProbe.userVisibleLibraryVersion();
  }

  /**
   * Resolves a class by FQN from this endpoint's classloader (= the user classloader). The IT uses
   * this to assert runtime-only types are invisible to user code while boundary types are visible.
   * Dots are valid path characters, so the FQN can be passed as a single path segment.
   */
  @Get("/classForName/{name}")
  public String classForName(String name) {
    try {
      return Class.forName(name).getName();
    } catch (ClassNotFoundException e) {
      return "[NOT FOUND]";
    }
  }
}
