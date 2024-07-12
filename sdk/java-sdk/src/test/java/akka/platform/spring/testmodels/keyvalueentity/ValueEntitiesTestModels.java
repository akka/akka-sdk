/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.keyvalueentity;

import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.JWT;
import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.spring.testmodels.Done;

public class ValueEntitiesTestModels {

  @TypeId("user")
  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class ValueEntityWithServiceLevelAcl extends KeyValueEntity<User> {
  }

  @TypeId("user")
  public static class ValueEntityWithMethodLevelAcl extends KeyValueEntity<User> {
    @Acl(allow = @Acl.Matcher(service = "test"))
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuer = {"a", "b"},
    staticClaims = {
      @JWT.StaticClaim(claim = "role", value = "admin"),
      @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
    })
  @TypeId("user")
  public static class ValueEntityWithServiceLevelJwt extends KeyValueEntity<User> {
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @TypeId("user")
  public static class ValueEntityWithMethodLevelJwt extends KeyValueEntity<User> {

    @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      bearerTokenIssuer = {"c", "d"},
      staticClaims = {
        @JWT.StaticClaim(claim = "role", value = "method-admin"),
        @JWT.StaticClaim(claim = "aud", value = "${ENV}")
      })
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @TypeId("user")
  public static class InvalidValueEntityWithOverloadedCommandHandler extends KeyValueEntity<User> {
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
    public KeyValueEntity.Effect<Done> createEntity(String user) {
      return effects().reply(Done.instance);
    }
  }
}
