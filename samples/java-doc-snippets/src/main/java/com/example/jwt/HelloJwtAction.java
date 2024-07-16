package com.example.jwt;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.JWT;
import akka.platform.javasdk.annotations.http.Post;

// tag::bearer-token[]
@ComponentId("hello-jwt")
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
