/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.mcp.McpEndpoint;

@McpEndpoint(serverName = "shop")
public class ShopMcpEndpoint {}
