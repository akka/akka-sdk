package customer.view;

// tag::class[]
import customer.api.CustomerEntity;
import customer.domain.Customer;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

import java.util.Collection;

@ViewId("view_customers_by_name")
@Table("customers_by_name")
public class CustomersByNameView
  extends View<CustomersByNameView.CustomerSummary> {

  public record CustomerSummary(String name, String email) {
  }

  public record Customers(Collection<CustomerSummary> customers) {
  }
  public record QueryParameters(String name) {
  }

  @Query("SELECT * AS customers FROM customers_by_name WHERE name = :name")
  public Customers getCustomers(QueryParameters params) {
    return null;
  }

  @Subscribe.ValueEntity(CustomerEntity.class) // <4>
  public UpdateEffect<CustomerSummary> onUpdate(Customer customer) {
    return effects()
      .updateState(new CustomerSummary(customer.name(), customer.email()));
  }
}
// end::class[]
