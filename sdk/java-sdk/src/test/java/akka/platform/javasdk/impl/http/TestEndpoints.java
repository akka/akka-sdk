/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.http;

import akka.http.javadsl.model.HttpResponse;
import akka.platform.javasdk.annotations.http.Delete;
import akka.platform.javasdk.annotations.http.Endpoint;
import akka.platform.javasdk.annotations.http.Get;
import akka.platform.javasdk.annotations.http.Patch;
import akka.platform.javasdk.annotations.http.Post;
import akka.platform.javasdk.annotations.http.Put;
import akka.platform.javasdk.http.HttpResponses;

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

}