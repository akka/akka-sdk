/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http;

import akka.NotUsed;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ws.Message;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.annotations.http.WebSocket;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TestEndpoints {

  record AThing(String someProperty) {}

  @HttpEndpoint("prefix")
  public static class TestEndpoint {

    public void nonHttpEndpointMethod() {}

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
    public void delete(String it) {}

    @Put("/{it}")
    public CompletionStage<String> update(String it, AThing theBody) {
      return CompletableFuture.completedFuture("ok");
    }

    @Patch("/{it}")
    public HttpResponse patch(String it, AThing theBody) {
      return HttpResponses.ok();
    }
  }

  public record SomeObject(
      @Description("some string") String someString,
      Optional<String> optionalString,
      boolean someBoolean,
      int someInt,
      @Description("optional double") Optional<Double> optionalDouble) {}

  @HttpEndpoint("test-specs")
  public static class TestEndpointSpecs {

    @Get("/parameters-only/{someString}/and/{someInt}")
    public String parametersOnly(
        @Description("some string") String someString, @Description("some int") int someInt) {
      return "ok";
    }

    @Post("/parameters-and-body/{someString}/and/{someDouble}")
    public String parametersAndBody(String someString, double someDouble, SomeObject someBody) {
      return "ok";
    }

    @Post("/body-only")
    public String bodyOnly(@Description("some body") SomeObject someBody) {
      return "ok";
    }

    @Post("/parameters-and-text-body/{someInt}")
    public String parametersAndTextBody(int someInt, @Description("some body") String someBody) {
      return "ok";
    }

    @Post("/text-body-only")
    public String textBodyOnly(@Description("some body") String someBody) {
      return "ok";
    }

    @Post("/parameters-and-array-body/{someString}/and/{someInt}")
    public String parametersAndArrayBody(String someString, int someInt, List<String> someBody) {
      return "ok";
    }

    @Post("/array-body-only")
    public String arrayBodyOnly(@Description("some body") List<Double> someBody) {
      return "ok";
    }

    @Post("/parameters-and-low-level-body/{someBoolean}")
    public String parametersAndLowLevelBody(
        @Description("some boolean") boolean someBoolean,
        @Description("some body") HttpEntity.Strict someBody) {
      return "ok";
    }

    @Post("/low-level-body-only")
    public String lowLevelBodyOnly(HttpRequest request) {
      return "ok";
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
    @Acl(allow = {@Acl.Matcher(service = "this"), @Acl.Matcher(service = "that")})
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
  @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      bearerTokenIssuers = {"a", "b"},
      staticClaims = {
        @JWT.StaticClaim(
            claim = "roles",
            values = {"viewer", "editor"}),
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
        @JWT.StaticClaim(
            claim = "roles",
            values = {"editor", "viewer"}),
        @JWT.StaticClaim(claim = "aud", values = "${ENV}.kalix.${ENV2}.io"),
        @JWT.StaticClaim(claim = "sub", pattern = "^sub-\\S+$")
      })
  @HttpEndpoint("my-endpoint")
  public static class TestEndpointJwtClassAndMethodLevel {

    @JWT(
        validate = JWT.JwtMethodMode.BEARER_TOKEN,
        bearerTokenIssuers = {"c", "d"},
        staticClaims = {
          @JWT.StaticClaim(
              claim = "roles",
              values = {"admin"}),
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
          @JWT.StaticClaim(
              claim = "roles",
              values = {"admin"}),
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

  @HttpEndpoint("/")
  public static class WithRootPrefix {

    @Get("/")
    public void root() {}

    @Get("a")
    public void a() {}

    @Get("/b")
    public void b() {}
  }

  // Valid WebSocket endpoints for positive testing
  @HttpEndpoint("websocket")
  public static class ValidWebSocketEndpoints {

    @WebSocket("/text")
    public Flow<String, String, NotUsed> textWebSocket() {
      return Flow.create();
    }

    @WebSocket("/binary")
    public Flow<ByteString, ByteString, NotUsed> binaryWebSocket() {
      return Flow.create();
    }

    @WebSocket("/message")
    public Flow<Message, Message, NotUsed> messageWebSocket() {
      return Flow.create();
    }

    @WebSocket("/with-path-param/{id}")
    public Flow<String, String, NotUsed> withPathParam(String id) {
      return Flow.create();
    }
  }

  // WebSocket with wrong return type (not Flow)
  @HttpEndpoint("invalid-websocket-return-type")
  public static class InvalidWebSocketReturnType {

    @WebSocket("/echo")
    public String wrongReturnType() {
      return "wrong";
    }
  }

  // WebSocket with different in/out message types
  @HttpEndpoint("invalid-websocket-different-types")
  public static class InvalidWebSocketDifferentTypes {

    @WebSocket("/echo")
    public Flow<String, ByteString, NotUsed> differentInOut() {
      return null;
    }
  }

  // WebSocket with unsupported message type
  @HttpEndpoint("invalid-websocket-message-type")
  public static class InvalidWebSocketMessageType {

    @WebSocket("/echo")
    public Flow<Integer, Integer, NotUsed> unsupportedType() {
      return null;
    }
  }

  // WebSocket with unsupported materialized value type
  @HttpEndpoint("invalid-websocket-mat-type")
  public static class InvalidWebSocketMatType {

    @WebSocket("/echo")
    public Flow<String, String, String> wrongMatType() {
      return null;
    }
  }

  // WebSocket with request body parameter
  @HttpEndpoint("invalid-websocket-body-param")
  public static class InvalidWebSocketBodyParam {

    @WebSocket("/echo")
    public Flow<String, String, NotUsed> withBodyParam(AThing body) {
      return null;
    }
  }
}
