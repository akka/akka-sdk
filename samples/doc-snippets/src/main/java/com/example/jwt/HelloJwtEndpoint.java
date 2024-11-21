package com.example.jwt;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.annotations.JWT;

// tag::bearer-token[]
@HttpEndpoint("/example-jwt") // <1>
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL)) // <2>
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, 
     bearerTokenIssuers = "my-issuer") // <1>
public class HelloJwtEndpoint extends AbstractHttpEndpoint {

    public String message(String msg) {
        //..
    // end::bearer-token[]
        return "ok! Claims: " + String.join(",", requestContext().getJwtClaims().allClaimNames());
    // tag::bearer-token[]
    }

    @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN,
         bearerTokenIssuers = "my-other-issuer")
    public String messageWithIssuer(String msg) { // <3>
        //..
    // end::bearer-token[]    
        return "ok! Claims: " + String.join(",", requestContext().getJwtClaims().allClaimNames());
    // tag::bearer-token[]        
    }  
}
// end::bearer-token[]
