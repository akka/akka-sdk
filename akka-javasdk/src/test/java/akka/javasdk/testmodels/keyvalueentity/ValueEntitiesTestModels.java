/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.Done;

public class ValueEntitiesTestModels {

  @ComponentId("user")
  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class ValueEntityWithServiceLevelAcl extends KeyValueEntity<User> {
  }

  @ComponentId("user")
  public static class ValueEntityWithMethodLevelAcl extends KeyValueEntity<User> {
    @Acl(allow = @Acl.Matcher(service = "test"))
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuers = {"a", "b"},
    staticClaims = {
      @JWT.StaticClaim(claim = "role", values = "admin"),
      @JWT.StaticClaim(claim = "aud", values = "${ENV}.kalix.io")
    })
  @ComponentId("user")
  public static class ValueEntityWithServiceLevelJwt extends KeyValueEntity<User> {
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @ComponentId("user")
  public static class ValueEntityWithMethodLevelJwt extends KeyValueEntity<User> {

    @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      bearerTokenIssuers = {"c", "d"},
      staticClaims = {
        @JWT.StaticClaim(claim = "role", values = "method-admin"),
        @JWT.StaticClaim(claim = "aud", values = "${ENV}")
      })
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @ComponentId("user")
  public static class InvalidValueEntityWithOverloadedCommandHandler extends KeyValueEntity<User> {
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
    public KeyValueEntity.Effect<Done> createEntity(String user) {
      return effects().reply(Done.instance);
    }
  }
}
