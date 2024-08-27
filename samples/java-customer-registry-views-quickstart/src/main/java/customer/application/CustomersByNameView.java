package customer.application;

// tag::class[]
import akka.platform.javasdk.view.TableUpdater;
import customer.domain.Customer;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

import java.util.Collection;

@ComponentId("view_customers_by_name")
public class CustomersByNameView extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class) // <4>
  public static class CustomerByNameUpdater extends TableUpdater<CustomerSummary> {
    public Effect<CustomerSummary> onUpdate(Customer customer) {
      return effects()
          .updateRow(new CustomerSummary(customer.name(), customer.email()));
    }
  }

  public record CustomerSummary(String name, String email) {
  }

  public record Customers(Collection<CustomerSummary> customers) {
  }
  public record QueryParameters(String name) {
  }

  @Query("SELECT * AS customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<Customers> getCustomers(QueryParameters params) {
    return queryResult();
  }
}
// end::class[]
