package store.order.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import store.order.application.CreateOrder;
import store.order.application.OrderEntity;
import store.order.domain.Order;
import store.order.view.joined.JoinedCustomerOrdersView;
import store.order.view.nested.CustomerOrders;
import store.order.view.nested.NestedCustomerOrdersView;
import store.order.view.structured.StructuredCustomerOrdersView;

import java.util.concurrent.CompletionStage;

import static akka.javasdk.http.HttpResponses.created;

@HttpEndpoint("/orders")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class OrderEndpoint {

  private final ComponentClient componentClient;

  public OrderEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{orderId}")
  public CompletionStage<HttpResponse> create(String orderId, CreateOrder createOrder) {
    return componentClient.forKeyValueEntity(orderId)
      .method(OrderEntity::create)
      .invokeAsync(createOrder)
      .thenApply(__ -> created());
  }

  @Get("/{orderId}")
  public CompletionStage<Order> get(String orderId) {
    return componentClient.forKeyValueEntity(orderId)
      .method(OrderEntity::get)
      .invokeAsync();
  }

  @Get("/joined-by-customer/{customerId}")
  public CompletionStage<JoinedCustomerOrdersView.CustomerOrders> joinedByCustomer(String customerId) {
    return componentClient.forView()
      .method(JoinedCustomerOrdersView::get)
      .invokeAsync(customerId);
  }

  @Get("/nested-by-customer/{customerId}")
  public CompletionStage<CustomerOrders> nestedByCustomer(String customerId) {
    return componentClient.forView()
      .method(NestedCustomerOrdersView::get)
      .invokeAsync(customerId);
  }

  @Get("/structured-by-customer/{customerId}")
  public CompletionStage<store.order.view.structured.CustomerOrders> structuredByCustomer(String customerId) {
    return componentClient.forView()
      .method(StructuredCustomerOrdersView::get)
      .invokeAsync(customerId);
  }
}
