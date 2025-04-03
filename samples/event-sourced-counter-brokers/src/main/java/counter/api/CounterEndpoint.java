package counter.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import counter.application.CounterByValueView;
import counter.application.CounterEntity;
import counter.application.CounterEntity.CounterResult.ExceedingMaxCounterValue;
import counter.application.CounterEntity.CounterResult.Success;
import counter.application.CounterTopicView;

import java.util.List;

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
  public Integer get(String counterId) {
    return componentClient.forEventSourcedEntity(counterId) // <2>
      .method(CounterEntity::get)
      .invoke(); // <3>
  }

  @Post("/{counterId}/increase/{value}")
  public HttpResponse increase(String counterId, Integer value) {
    componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::increase)
      .invoke(value);

    return ok(); // <4>
  }
  // end::endpoint-component-interaction[]

  //tag::increaseWithError[]
  @Post("/{counterId}/increase-with-error/{value}")
  public Integer increaseWithError(String counterId, Integer value) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::increaseWithError)
      .invoke(value); // <1>
  }
  //end::increaseWithError[]

  //tag::increaseWithResult[]
  @Post("/{counterId}/increase-with-result/{value}")
  public HttpResponse increaseWithResult(String counterId, Integer value) {
    var counterResult = componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::increaseWithResult)
      .invoke(value);

    return switch (counterResult) { // <1>
      case Success success -> ok(success.value());
      case ExceedingMaxCounterValue e -> badRequest(e.message());
    };
  }
  //end::increaseWithResult[]

  @Post("/{counterId}/multiply/{value}")
  public Integer multiply(String counterId, Integer value) {
    return componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::multiply)
      .invoke(value);
  }

  @Get("/greater-than/{value}")
  public CounterByValueView.CounterByValueList greaterThan(Integer value) {
    return componentClient.forView()
      .method(CounterByValueView::findByCountersByValueGreaterThan)
      .invoke(value);
  }

  @Get("/all")
  public CounterByValueView.CounterByValueList getAll() {
    return componentClient.forView()
      .method(CounterByValueView::findAll)
      .invoke();
  }

  @Get("/greater-than-via-topic/{value}")
  public CounterTopicView.CountersResult greaterThanViaTopic(Integer value) {
    return componentClient.forView()
        .method(CounterTopicView::countersHigherThan)
        .invoke(value);
  }

  // tag::concurrent-endpoint-component-interaction[]
  public record IncreaseAllThese(List<String> counterIds, Integer value) {}
  @Post("/increase-multiple")
  public HttpResponse increaseMultiple(IncreaseAllThese increaseAllThese) throws Exception {
    var triggeredTasks = increaseAllThese.counterIds().stream().map(counterId ->
        componentClient.forEventSourcedEntity(counterId)
            .method(CounterEntity::increase)
            .invokeAsync(increaseAllThese.value) // <1>
        ).toList();

    for (var task : triggeredTasks) {
      task.toCompletableFuture().get(); // <2>
    }
    return ok(); // <3>
  }
  // end::concurrent-endpoint-component-interaction[]

// tag::endpoint-component-interaction[]
}
// end::endpoint-component-interaction[]