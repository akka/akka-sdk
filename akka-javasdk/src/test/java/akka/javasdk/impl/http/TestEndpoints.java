/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Endpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.http.HttpResponses;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TestEndpoints {

    record AThing(String someProperty) {}

    @Endpoint("prefix")
    public static class TestEndpoint {

        public void nonHttpEndpointMethod() {

        }

        @Get("/")
        public String list() {
            return "some things";
        }

        @Get("/{it}")
        public AThing get(String it) {
            return new AThing("thing for " + it);
        }

        @Post("/{it}")
        public String create(String it, AThing theBody) {
            return "ok";
        }

        @Delete("/{it}")
        public void delete(String it) {
        }

        @Put("/{it}")
        public CompletionStage<String> update(String it, AThing theBody) {
            return CompletableFuture.completedFuture("ok");
        }

        @Patch("/{it}")
        public HttpResponse patch(String it, AThing theBody) {
            return HttpResponses.ok();
        }
    }

    @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL))
    @Endpoint("acls")
    public static class TestEndpointAcls {

        @Get("/no-acl")
        public String noAcl() {
            return "no-acl";
        }

        @Get("/secret")
        @Acl(
            allow = @Acl.Matcher(service = "backoffice-service"),
            deny = @Acl.Matcher(principal = Acl.Principal.INTERNET))
        public String secret() {
            return "the greatest secret";
        }

        @Get("/this-and-that")
        @Acl(allow = { @Acl.Matcher(service = "this"), @Acl.Matcher(service = "that") })
        public String thisAndThat() {
            return "this-and-that";
        }

    }

    @Endpoint("invalid-acl")
    public static class TestEndpointInvalidAcl {
        @Get("/invalid")
        @Acl(allow = @Acl.Matcher(service = "*", principal = Acl.Principal.INTERNET))
        public String invalid() {
            return "invalid matcher";
        }

    }

}