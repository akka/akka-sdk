package customer.view;

// tag::class[]
import customer.api.CustomerEntity;
import customer.domain.Customer;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

import java.util.Collection;

@ComponentId("view_customers_by_name")
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

  @Consume.FromKeyValueEntity(CustomerEntity.class) // <4>
  public Effect<CustomerSummary> onUpdate(Customer customer) {
    return effects()
      .updateState(new CustomerSummary(customer.name(), customer.email()));
  }
}
// end::class[]
