package counter.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import counter.application.CounterEntity;
import counter.application.CounterEntity.CounterResult.ExceedingMaxCounterValue;
import counter.application.CounterEntity.CounterResult.Success;

import java.util.concurrent.CompletionStage;

import static akka.javasdk.http.HttpResponses.badRequest;
import static akka.javasdk.http.HttpResponses.ok;

// tag::endpoint-component-interaction[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/counter")
public class CounterEndpoint {

  private final ComponentClient componentClient;

  public CounterEndpoint(ComponentClient componentClient) { //<1>
    this.componentClient = componentClient;
  }

  @Get("/{counterId}")
  public CompletionStage<Integer> get(String counterId) {
    return componentClient.forEventSourcedEntity(counterId) // <2>
      .method(CounterEntity::get)
      .invokeAsync(); // <3>
  }

  @Post("/{counterId}/increase/{value}")
  public CompletionStage<HttpResponse> increase(String counterId, Integer value) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::increase)
      .invokeAsync(value)
      .thenApply(__ -> ok()); // <4>
  }
  // end::endpoint-component-interaction[]

  //tag::increaseWithError[]
  @Post("/{counterId}/increase-with-error/{value}")
  public CompletionStage<Integer> increaseWithError(String counterId, Integer value) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::increaseWithError)
      .invokeAsync(value); // <1>
  }
  //end::increaseWithError[]

  //tag::increaseWithResult[]
  @Post("/{counterId}/increase-with-result/{value}")
  public CompletionStage<HttpResponse> increaseWithResult(String counterId, Integer value) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::increaseWithResult)
      .invokeAsync(value)
      .thenApply(counterResult ->
        switch (counterResult) { // <1>
          case Success success -> ok(success.value());
          case ExceedingMaxCounterValue e -> badRequest(e.message());
        });
  }
  //end::increaseWithResult[]

  @Post("/{counterId}/multiply/{value}")
  public CompletionStage<Integer> multiply(String counterId, Integer value) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::multiply)
      .invokeAsync(value);
  }

  public record CounterRequest(String id, Integer value) {
  }
// tag::endpoint-component-interaction[]
}
// end::endpoint-component-interaction[]