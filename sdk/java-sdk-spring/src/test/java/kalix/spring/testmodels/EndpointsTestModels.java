/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels;

import akka.http.javadsl.model.HttpResponse;
import kalix.javasdk.annotations.http.Delete;
import kalix.javasdk.annotations.http.Endpoint;
import kalix.javasdk.annotations.http.Get;
import kalix.javasdk.annotations.http.Patch;
import kalix.javasdk.annotations.http.Post;
import kalix.javasdk.annotations.http.Put;
import kalix.javasdk.http.HttpResponses;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class EndpointsTestModels {

  public record Name(String value) {
    @Override
    public String toString() {
      return value;
    }
  }

  @Endpoint("/hello")
  public static class GetHelloEndpoint {

    /**
     * For testing response returning a String
     */
    @Get
    public String hello() {
      return "Hello";
    }

    /**
     * For testing a response returning a model object
     * resulting in an application/json response
     */
    @Get("/{name}")
    public Name name(String name) {
      return new Name(name);
    }


    @Get("/{name}/{age}")
    public String nameAndAge(String name, int age) {
      return "name: " + name + ", age: " +  age;
    }

    /**
     * For testing  response returning an Akka HttpResponse directly
     */
    @Get("/{name}/{age}/http-response")
    public HttpResponse fixedNameAndAgeHttp(String name, int age) {
      return HttpResponses.Ok("http => name: " + name + ", age: " + age);
    }

    @Get("/name/{age}")
    public String fixedNameAndAge(int age) {
      return "name: fixed" + ", age: " +  age;
    }

    /**
     * For testing async response
     */
    @Get("/{name}/{age}/async")
    public CompletionStage<String> nameAndAgeAsync(String name, int age) {
      return CompletableFuture.completedStage("async => name: " + name + ", age: " +  age);
    }

    /**
     * For testing async response wrapping an Akka HttpResponse
     */
    @Get("/{name}/{age}/async/http-response")
    public CompletionStage<HttpResponse> nameAndAgeAsyncHttp(String name, int age) {
      return CompletableFuture.completedStage(HttpResponses.Ok("async http => name: " + name + ", age: " + age));
    }

  }

  @Endpoint("/hello")
  public static class PostHelloEndpoint {

    @Post
    public String namePost(Name body) {
      return "name: " + body.toString();
    }

    @Post("/{age}")
    public String nameAndAgePost(int age, Name body) {
      return "name: " + body.toString() + ", age: " +  age;
    }

    @Post("/{age}/async")
    public CompletionStage<String> nameAndAgePostAsync(int age, Name body) {
      return CompletableFuture.completedStage("async => name: " + body.toString() + ", age: " +  age);
    }

  }


  @Endpoint("/hello")
  public static class PutHelloEndpoint {
    @Put
    public String helloPut(Name body) {
      return "name: " + body.toString();
    }
  }

  @Endpoint("/hello")
  public static class PatchHelloEndpoint {
    @Patch
    public String helloPatch(Name body) {
      return "name: " + body.toString();
    }
  }

  @Endpoint("/hello")
  public static class DeleteHelloEndpoint {

    @Delete
    public void helloDelete() {

    }
  }

}
