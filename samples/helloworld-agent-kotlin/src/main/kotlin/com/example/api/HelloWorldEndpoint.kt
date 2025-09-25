package com.example.api

import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.annotations.http.Post
import akka.javasdk.client.ComponentClient
import com.example.application.HelloWorldAgent

/**
 * This is a simple Akka Endpoint that uses an agent and LLM to generate
 * greetings in different languages.
 */
// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = [Acl.Matcher(principal = Acl.Principal.INTERNET)])
@HttpEndpoint
class HelloWorldEndpoint(private val componentClient: ComponentClient) {

    data class Request(val user: String, val text: String)

    @Post("/hello")
    fun hello(request: Request): String {
        return componentClient
            .forAgent()
            .inSession(request.user)
            .method(HelloWorldAgent::greet)
            .invoke(request.text)
    }
}