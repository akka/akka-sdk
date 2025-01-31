/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Acl.Matcher;
import akka.javasdk.annotations.Acl.Principal;

public class AclTestModels {

  public static class MainWithoutAnnotation {}

  @Acl(allow = @Matcher(service = "*"))
  public static class MainAllowAllServices {}

  @Acl(allow = {@Matcher(service = "foo"), @Matcher(service = "bar")})
  public static class MainAllowListOfServices {}

  @Acl(allow = @Matcher(principal = Principal.INTERNET))
  public static class MainAllowPrincipalInternet {}

  @Acl(allow = @Matcher(principal = Principal.ALL))
  public static class MainAllowPrincipalAll {}

  @Acl(allow = @Matcher(principal = Principal.ALL, service = "*"))
  public static class MainWithInvalidAllowAnnotation {}

  @Acl(deny = @Matcher(service = "*"))
  public static class MainDenyAllServices {}

  @Acl(deny = {@Matcher(service = "foo"), @Matcher(service = "bar")})
  public static class MainDenyListOfServices {}

  @Acl(deny = @Matcher(principal = Principal.INTERNET))
  public static class MainDenyPrincipalInternet {}

  @Acl(deny = @Matcher(principal = Principal.ALL))
  public static class MainDenyPrincipalAll {}

  @Acl(allow = @Matcher(principal = Principal.ALL, service = "*"))
  public static class MainWithInvalidDenyAnnotation {}

  @Acl(denyCode = 409)
  public static class MainDenyWithCode {}
}
