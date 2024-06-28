package com.example.jwt;

import kalix.javasdk.StatusCode;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ActionId;
import kalix.javasdk.annotations.JWT;

@ActionId("jwt-action")
public class JWTAction extends Action {

    // tag::bearer-token[]
    @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN) // <1>
    public Action.Effect<String> message(String msg) {
        return effects().reply(msg);
    }
    // end::bearer-token[]

    // tag::bearer-token-issuer[]
    @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN,
         bearerTokenIssuer = "my-issuer")       // <1>
    public Action.Effect<String> messageWithIssuer(String msg) {
        return effects().reply(msg);
    }
    // end::bearer-token-issuer[]


    // tag::bearer-token-multi-issuer[]
    @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN,
         bearerTokenIssuer = {"my-issuer", "my-other-issuer"}) // <1>
    public Action.Effect<String> messageWithMultiIssuer(String msg) {
        return effects().reply(msg);
    }
    // end::bearer-token-multi-issuer[]

    // tag::bearer-token-claims[]
    @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
    public Action.Effect<String> messageWithClaimValidation(String msg) {
        var maybeSubject = actionContext().metadata().jwtClaims().subject();
        if (maybeSubject.isEmpty())
            return effects().error("No subject present", StatusCode.ErrorCode.UNAUTHORIZED);

        return effects().reply(msg);
    }
    // end::bearer-token-claims[]
}
