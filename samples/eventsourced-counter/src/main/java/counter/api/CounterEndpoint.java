package counter.api;

import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import counter.application.CounterEntity;

import java.util.concurrent.CompletionStage;

@HttpEndpoint("/counter")
public class CounterEndpoint {

    private final ComponentClient componentClient;

    public CounterEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/increase")
    public CompletionStage<String> increase(CounterRequest request) {
        return componentClient.forEventSourcedEntity(request.id())
            .method(CounterEntity::increase)
            .invokeAsync(request.value());
    }

    @Post("/multiply")
    public CompletionStage<String> multiply(CounterRequest request) {
        return componentClient.forEventSourcedEntity(request.id())
            .method(CounterEntity::multiply)
            .invokeAsync(request.value());
    }

    @Get("/{counterId}")
    public CompletionStage<Integer> get(String counterId) {
        return componentClient.forEventSourcedEntity(counterId)
            .method(CounterEntity::get)
            .invokeAsync();
    }

    public record CounterRequest(String id, Integer value) {}

}
