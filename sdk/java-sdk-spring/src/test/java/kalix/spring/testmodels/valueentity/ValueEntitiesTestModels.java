/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels.valueentity;

import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.JWT;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.spring.testmodels.Done;

public class ValueEntitiesTestModels {

  @TypeId("user")
  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class ValueEntityWithServiceLevelAcl extends ValueEntity<User> {
  }

  @TypeId("user")
  public static class ValueEntityWithMethodLevelAcl extends ValueEntity<User> {
    @Acl(allow = @Acl.Matcher(service = "test"))
    public ValueEntity.Effect<Done> createEntity(CreateUser createUser) {
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
  public static class ValueEntityWithServiceLevelJwt extends ValueEntity<User> {
    public ValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @TypeId("user")
  public static class ValueEntityWithMethodLevelJwt extends ValueEntity<User> {

    @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      bearerTokenIssuer = {"c", "d"},
      staticClaims = {
        @JWT.StaticClaim(claim = "role", value = "method-admin"),
        @JWT.StaticClaim(claim = "aud", value = "${ENV}")
      })
    public ValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
  }

  @TypeId("user")
  public static class InvalidValueEntityWithOverloadedCommandHandler extends ValueEntity<User> {
    public ValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }
    public ValueEntity.Effect<Done> createEntity(String user) {
      return effects().reply(Done.instance);
    }
  }
}
