package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;

@HttpEndpoint("/api/orgs")
public class ValidHttpEndpointWithMultiplePathParams {

  @Get("/{orgId}/users/{userId}")
  public String getUser(String orgId, String userId) {
    return "org " + orgId + " user " + userId;
  }

  @Get("/{orgId}/users/{userId}/details/{detailId}")
  public String getUserDetail(String orgId, String userId, String detailId) {
    return "org " + orgId + " user " + userId + " detail " + detailId;
  }

  @Post("/{orgId}/users/{userId}")
  public String createUser(String orgId, String userId, String body) {
    return "create";
  }
}
