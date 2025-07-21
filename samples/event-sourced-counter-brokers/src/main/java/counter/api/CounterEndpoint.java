package counter.api;

import static akka.javasdk.http.HttpResponses.badRequest;
import static akka.javasdk.http.HttpResponses.ok;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.CommandException;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import counter.application.CounterByValueView;
import counter.application.CounterEntity;
import counter.application.CounterEntity.CounterLimitExceededException;
import counter.application.CounterTopicView;
import java.util.List;

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
    return componentClient
      .forEventSourcedEntity(counterId) // <2>
      .method(CounterEntity::get)
      .invoke(); // <3>
  }

  @Post("/{counterId}/increase/{value}")
  public HttpResponse increase(String counterId, Integer value) {
    componentClient
      .forEventSourcedEntity(counterId)
      .method(CounterEntity::increase)
      .invoke(value);

    return ok(); // <4>
  }

  // end::endpoint-component-interaction[]

  //tag::increaseWithError[]
  @Post("/{counterId}/increase-with-error/{value}")
  public Integer increaseWithError(String counterId, Integer value) {
    return componentClient
      .forEventSourcedEntity(counterId)
      .method(CounterEntity::increaseWithError)
      .invoke(value); // <1>
  }

  //end::increaseWithError[]

  //tag::increaseWithErrorHandling[]
  @Post("/{counterId}/increase-with-error-handling/{value}")
  public HttpResponse increaseWithErrorHandling(String counterId, Integer value) {
    try {
      var result = componentClient
        .forEventSourcedEntity(counterId)
        .method(CounterEntity::increaseWithError)
        .invoke(value);
      return ok(result);
    } catch (CommandException e) { // <1>
      return badRequest("rejected: " + value);
    }
  }

  //end::increaseWithErrorHandling[]

  //tag::increaseWithException[]
  @Post("/{counterId}/increase-with-exception/{value}")
  public HttpResponse increaseWithException(String counterId, Integer value) {
    try {
      var result = componentClient
        .forEventSourcedEntity(counterId)
        .method(CounterEntity::increaseWithException)
        .invoke(value);
      return ok(result);
    } catch (CounterLimitExceededException e) { // <1>
      return badRequest("rejected: " + e.getValue());
    }
  }

  //end::increaseWithException[]

  @Post("/{counterId}/multiply/{value}")
  public Integer multiply(String counterId, Integer value) {
    return componentClient
      .forEventSourcedEntity(counterId)
      .method(CounterEntity::multiply)
      .invoke(value);
  }

  @Get("/greater-than/{value}")
  public CounterByValueView.CounterByValueList greaterThan(Integer value) {
    return componentClient
      .forView()
      .method(CounterByValueView::findByCountersByValueGreaterThan)
      .invoke(value);
  }

  @Get("/all")
  public CounterByValueView.CounterByValueList getAll() {
    return componentClient.forView().method(CounterByValueView::findAll).invoke();
  }

  @Get("/greater-than-via-topic/{value}")
  public CounterTopicView.CountersResult greaterThanViaTopic(Integer value) {
    return componentClient
      .forView()
      .method(CounterTopicView::countersHigherThan)
      .invoke(value);
  }

  // tag::concurrent-endpoint-component-interaction[]
  public record IncreaseAllThese(List<String> counterIds, Integer value) {}

  @Post("/increase-multiple")
  public HttpResponse increaseMultiple(IncreaseAllThese increaseAllThese) throws Exception {
    var triggeredTasks = increaseAllThese
      .counterIds()
      .stream()
      .map(
        counterId ->
          componentClient
            .forEventSourcedEntity(counterId)
            .method(CounterEntity::increase)
            .invokeAsync(increaseAllThese.value)
      ) // <1>
      .toList();

    for (var task : triggeredTasks) {
      task.toCompletableFuture().get(); // <2>
    }
    return ok(); // <3>
  }
  // end::concurrent-endpoint-component-interaction[]

  // tag::endpoint-component-interaction[]
}
// end::endpoint-component-interaction[]
