/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.mcp.McpEndpoint;

@McpEndpoint(serverName = "shop", path = "/shop-mcp")
@Acl(allow = @Acl.Matcher(service = "assistant", scope = Acl.Scope.PARENT_PROJECT))
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "mcp-issuer")
public class ShopMcpEndpoint {}
