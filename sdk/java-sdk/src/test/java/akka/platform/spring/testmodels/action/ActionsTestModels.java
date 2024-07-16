/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.action;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.spring.testmodels.Message;

public class ActionsTestModels {

  @ComponentId("test-action-0")
  public static class ActionWithoutParam extends Action {
    public Action.Effect<Message> message() {
      return effects().reply(new Message("hello"));
    }
  }

  @ComponentId("test-action-1")
  public static class ActionWithOneParam extends Action {
    public Action.Effect<Message> message(String one) {
      return effects().reply(new Message(one));
    }
  }



  /* FIXME cover these for endpoints
  public static class ActionWithMethodLevelJWT extends Action {
    @PostMapping("/message")
    @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      bearerTokenIssuer = {"a", "b"},
      staticClaims = {
          @JWT.StaticClaim(claim = "roles", value = {"viewer", "editor"}),
          @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io"),
          @JWT.StaticClaim(claim = "sub", pattern = "^sub-\\S+$")
      })
    public Action.Effect<Message> message(@RequestBody Message msg) {
      return effects().reply(msg);
    }
  }


  @JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuer = {"a", "b"},
    staticClaims = {
        @JWT.StaticClaim(claim = "roles", value = {"editor", "viewer"}),
        @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io"),
        @JWT.StaticClaim(claim = "sub", pattern = "^\\S+$")
    })
  public static class ActionWithServiceLevelJWT extends Action {
    @PostMapping("/message")
    public Action.Effect<Message> message(@RequestBody Message msg) {
      return effects().reply(msg);
    }
  }
  */
}
