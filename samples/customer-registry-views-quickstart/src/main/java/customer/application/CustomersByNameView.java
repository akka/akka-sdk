package customer.application;

// tag::class[]
import akka.javasdk.view.TableUpdater;
import customer.domain.Customer;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

import java.util.Collection;

@ComponentId("view_customers_by_name")
public class CustomersByNameView extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class) // <4>
  public static class CustomerByNameUpdater extends TableUpdater<CustomerSummary> {
    public Effect<CustomerSummary> onUpdate(Customer customer) {
      return effects()
          .updateRow(new CustomerSummary(customer.customerId(), customer.name(), customer.email()));
    }
  }

  public record CustomerSummary(String customerId, String name, String email) { }

  public record Customers(Collection<CustomerSummary> customers) { }

  @Query("SELECT * AS customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<Customers> getCustomers(String name) {
    return queryResult();
  }

  @Query("SELECT * FROM customers_by_name WHERE name = :name")
  public QueryStreamEffect<CustomerSummary> getCustomerSummaryStream(String name) {
    return queryStreamResult();
  }

  @Query(value = "SELECT * FROM customers_by_name WHERE name = :name", streamUpdates = true)
  public QueryStreamEffect<CustomerSummary> continousGetCustomerSummaryStream(String name) {
    return queryStreamResult();
  }

}
// end::class[]
