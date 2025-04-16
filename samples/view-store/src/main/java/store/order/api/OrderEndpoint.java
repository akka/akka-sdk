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
import store.order.view.joined.JoinedCustomerOrdersView.JoinedCustomerOrders;
import store.order.view.nested.NestedCustomerOrders;
import store.order.view.nested.NestedCustomerOrdersView;
import store.order.view.structured.StructuredCustomerOrders;
import store.order.view.structured.StructuredCustomerOrdersView;

import static akka.javasdk.http.HttpResponses.created;

@HttpEndpoint("/orders")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class OrderEndpoint {

  private final ComponentClient componentClient;

  public OrderEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{orderId}")
  public HttpResponse create(String orderId, CreateOrder createOrder) {
    componentClient.forKeyValueEntity(orderId)
      .method(OrderEntity::create)
      .invoke(createOrder);

    return created();
  }

  @Get("/{orderId}")
  public Order get(String orderId) {
    return componentClient.forKeyValueEntity(orderId)
      .method(OrderEntity::get)
      .invoke();
  }

  @Get("/joined-by-customer/{customerId}")
  public JoinedCustomerOrders joinedByCustomer(String customerId) {
    return componentClient.forView()
      .method(JoinedCustomerOrdersView::get)
      .invoke(customerId);
  }

  @Get("/nested-by-customer/{customerId}")
  public NestedCustomerOrders nestedByCustomer(String customerId) {
    return componentClient.forView()
      .method(NestedCustomerOrdersView::get)
      .invoke(customerId);
  }

  @Get("/structured-by-customer/{customerId}")
  public StructuredCustomerOrders structuredByCustomer(String customerId) {
    return componentClient.forView()
      .method(StructuredCustomerOrdersView::get)
      .invoke(customerId);
  }
}
