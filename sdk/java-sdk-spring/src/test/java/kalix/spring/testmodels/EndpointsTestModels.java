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

import java.util.Optional;
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

    @Get("/name/{age}")
    public String fixedNameAndAge(int age) {
      return "name: fixed" + ", age: " + age;
    }

    @Get("/{name}/{age}")
    public String nameAndAge(String name, int age) {
      return "name: " + name + ", age: " +  age;
    }

    /**
     * For testing  response returning an Akka HttpResponse directly
     */
    @Get("/{name}/{age}/http-response")
    public HttpResponse nameAndAgeHttp(String name, int age) {
      return HttpResponses.ok("http => name: " + name + ", age: " + age);
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
      return CompletableFuture.completedStage(HttpResponses.ok("async http => name: " + name + ", age: " + age));
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
      // test coverage for return void
    }

    @Delete("/{name}")
    public String helloDelete(String name) {
      // test coverage for return null
      return null;
    }
  }


  public static class Foo {
    @Get("/foo")
    public void doFooThings() {}
  }

  public static class FooBar {
    @Get("/foo/bar")
    public void doBarThings() {}
  }

  public static class FooBarBaz {
    @Get("/foo/bar/baz")
    public void doBazThings() {}
  }

  public static class FooBarBazWithInt {
    @Get("/foo/{num}/baz")
    public void doBazThingsWithInt(int i) {}
  }

  public static class TestShort {
    @Get("/short/{num}")
    public void primitive(short i) {
    }

    @Get("/short/{num}/boxed")
    public void boxed(Short i) {
    }
  }

  public static class FooWithDoubleMapping1 {
    @Get("/foo/bar/baz")
    public void method1() {}
  }

  public static class FooWithDoubleMapping2 {
    @Get("/foo/bar/baz")
    public void method2() {}
  }

  public static class TestInt {
    @Get("/int/{num}")
    public void primitive(int i) {
    }

    @Get("/int/{num}/boxed")
    public void boxed(Integer i) {
    }
  }

  public static class TestLong {
    @Get("/long/{num}")
    public void primitive(long i) {
    }

    @Get("/long/{num}/boxed")
    public void boxed(Long i) {
    }
  }

  public static class TestShortIntLong {
    @Get("/number/{shortNum}")
    public void shortNum(short i) {
    }

    @Get("/number/{intNum}")
    public void intNum(int i) {
    }

    @Get("/number/{longNum}")
    public void longNum(long i) {
    }
  }

  public static class TestDouble {
    @Get("/double/{num}")
    public void primitive(double i) {
    }

    @Get("/double/{num}/boxed")
    public void boxed(Double i) {
    }
  }

  public static class TestFloat {
    @Get("/float/{num}")
    public void primitive(float i) {
    }

    @Get("/float/{num}/boxed")
    public void boxed(Float i) {
    }
  }

  public static class TestFloatDouble {
    @Get("/number/{floatNum}")
    public void floatNum(float i) {
    }

    @Get("/number/{doubleNum}")
    public void doubleNum(double i) {
    }
  }

  public static class TestString {
    @Get("/string/{name}")
    public void name(String i) {
    }
  }

  public static class TestChar {
    @Get("/char/{name}")
    public void name(Character i) {
    }
  }

  public static class TestBoolean {
    @Get("/bool/{bool}")
    public void primitive(boolean i) {
    }

    @Get("/bool/{bool}/boxed")
    public void boxed(Boolean i) {
    }
  }

  public static class TestMultiVar {
    @Get("/multi/{num}/{bool}/{double}")
    public void intBooleanDouble(int i , boolean s, double d) {
    }

    @Get("/multi/{name}/{long}/{float}")
    public void stringLongFloat(String s, Long l, Float f) {
    }
  }

  public static class TestUnsupportedType {
    @Get("/unsupported/{name}")
    public void unsupported(Optional<String> i) {
    }
  }
}
