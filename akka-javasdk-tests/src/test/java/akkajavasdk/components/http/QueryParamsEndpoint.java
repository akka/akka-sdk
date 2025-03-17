/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;

@HttpEndpoint()
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class QueryParamsEndpoint extends AbstractHttpEndpoint {

  @Get("/query/{name}")
  public String get(String name) {
    String a = requestContext().queryParams().getString("a").get();
    Integer b = requestContext().queryParams().getInteger("b").get();
    Long c = requestContext().queryParams().getLong("c").get();
    return "name: " + name + ", a: " + a + ", b: " + b + ", c: " + c;
  }
}
