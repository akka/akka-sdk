package com.example.tracing.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionStage;

public class Typicode {
    public static final String url = "https://jsonplaceholder.typicode.com/posts";
    // using a third party HTTP client here rather than the built in `akka.javasdk.http.HttpClient`
    // in order to showcase external/manual tracing
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public record TypicodePost(String userId, String id, String title, String body) {}

    public CompletionStage<HttpResponse<TypicodePost>> callAsyncService(String postID) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url + "/" + postID))
                .build();
        //Async call to external service
        return httpClient.sendAsync(httpRequest,
                new JsonResponseHandler<>(TypicodePost.class));
    }

    public static class JsonResponseHandler<T> implements HttpResponse.BodyHandler<T> {
        private final Class<T> responseType;

        public JsonResponseHandler(Class<T> responseType){
            this.responseType = responseType;
        }

        @Override
        public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
            return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(Charsets.UTF_8), responseBody -> {
                try {
                    return new ObjectMapper().readValue(responseBody, responseType);
                } catch (IOException e){
                    throw new RuntimeException("Failed to parse JSON");
                }
            });
        }
    }
}
