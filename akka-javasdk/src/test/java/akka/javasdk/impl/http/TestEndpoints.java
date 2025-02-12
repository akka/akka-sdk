/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.http.HttpResponses;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TestEndpoints {

    record AThing(String someProperty) {}

    @HttpEndpoint("prefix")
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

    @Acl(deny = @Acl.Matcher(principal = Acl.Principal.ALL), denyCode = 404)
    @HttpEndpoint("acls")
    public static class TestEndpointAcls {

        @Get("/no-acl")
        public String noAcl() {
            return "no-acl";
        }

        @Get("/secret")
        @Acl(
            allow = @Acl.Matcher(service = "backoffice-service"),
            deny = @Acl.Matcher(principal = Acl.Principal.INTERNET),
            denyCode = 401)
        public String secret() {
            return "the greatest secret";
        }

        @Get("/this-and-that")
        @Acl(allow = { @Acl.Matcher(service = "this"), @Acl.Matcher(service = "that") })
        public String thisAndThat() {
            return "this-and-that";
        }

    }

    @HttpEndpoint("invalid-acl")
    public static class TestEndpointInvalidAcl {
        @Get("/invalid")
        @Acl(allow = @Acl.Matcher(service = "*", principal = Acl.Principal.INTERNET))
        public String invalid() {
            return "invalid matcher";
        }
    }

    @HttpEndpoint("invalid-acl-denycode")
    public static class TestEndpointInvalidAclDenyCode {
        @Get("/invalid")
        @Acl(allow = @Acl.Matcher(service = "*"), denyCode = 123123)
        public String invalid() {
            return "invalid matcher";
        }
    }

    @HttpEndpoint("my-endpoint")
    @JWT(validate =
            JWT.JwtMethodMode.BEARER_TOKEN,
            bearerTokenIssuers = {"a", "b"},
            staticClaims = {
                        @JWT.StaticClaim(claim = "roles", values = {"viewer", "editor"}),
                        @JWT.StaticClaim(claim = "aud", values = "${ENV}.kalix.io"),
                        @JWT.StaticClaim(claim = "sub", pattern = "^sub-\\S+$")
                })
    public static class TestEndpointJwtClassLevel {
        @Get("/my-object/{id}")
        public String message(String id) {
            return "OK";
        }
    }

    @JWT(
            validate = JWT.JwtMethodMode.BEARER_TOKEN,
            bearerTokenIssuers = {"a", "b"},
            staticClaims = {
                    @JWT.StaticClaim(claim = "roles", values = {"editor", "viewer"}),
                    @JWT.StaticClaim(claim = "aud", values = "${ENV}.kalix.${ENV2}.io"),
                    @JWT.StaticClaim(claim = "sub", pattern = "^sub-\\S+$")
            })
    @HttpEndpoint("my-endpoint")
    public static class TestEndpointJwtClassAndMethodLevel {

        @JWT(
                validate = JWT.JwtMethodMode.BEARER_TOKEN,
                bearerTokenIssuers = {"c", "d"},
                staticClaims = {
                        @JWT.StaticClaim(claim = "roles", values = {"admin"}),
                        @JWT.StaticClaim(claim = "aud", values = "${ENV}.kalix.dev"),
                        @JWT.StaticClaim(claim = "sub", pattern = "^-\\S+$")
                })
        @Get("/my-object/{id}")
        public String message(String id) {
            return "OK";
        }
    }

    @HttpEndpoint("my-endpoint")
    public static class TestEndpointJwtOnlyMethodLevel {

        @JWT(
                validate = JWT.JwtMethodMode.BEARER_TOKEN,
                bearerTokenIssuers = {"c", "d"},
                staticClaims = {
                        @JWT.StaticClaim(claim = "roles", values = {"admin"}),
                        @JWT.StaticClaim(claim = "aud", values = "${ENV}.kalix.dev"),
                        @JWT.StaticClaim(claim = "sub", pattern = "^-\\S+$")
                })
        @Get("/my-object/{id}")
        public String message(String id) {
            return "OK";
        }
    }

    @HttpEndpoint("/{id}/my-endpoint")
    public static class InvalidEndpointMethods {

        // missing parameter
        @Get("/")
        public void list1() {}

        // wrong parameter name
        @Get("/")
        public void list2(String bob) {}

        // ok parameter count, wrong parameter name
        @Get("/something/{bob}")
        public void list3(String id, String value) {}

        // ok parameter count, body as last param
        @Get("/something")
        public void list4(String id, String body) {}

        // too many parameters
        @Get("/too-many")
        public void list5(String id, String value, String body) {}

        @Get("/wildcard/**/not/last")
        public void invalidWildcard(String id) {}
    }
}
