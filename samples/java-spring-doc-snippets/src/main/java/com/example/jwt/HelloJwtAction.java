package com.example.jwt;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ActionId;
import kalix.javasdk.annotations.JWT;
import kalix.javasdk.annotations.http.Post;

// tag::bearer-token[]
@ActionId("hello-jwt")
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, 
     bearerTokenIssuer = "my-issuer") // <1>
public class HelloJwtAction extends Action {

    public Action.Effect<String> message(String msg) {
        //..
    // end::bearer-token[]    
        return effects().reply(msg);
    // tag::bearer-token[]
    }

    @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN,
         bearerTokenIssuer = "my-other-issuer")     
    public Action.Effect<String> messageWithIssuer(String msg) { // <3>
        //..
    // end::bearer-token[]    
        return effects().reply(msg);
    // tag::bearer-token[]        
    }  
}
// end::bearer-token[]
