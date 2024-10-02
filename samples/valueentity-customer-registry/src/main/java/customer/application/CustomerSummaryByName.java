package customer.application;

import akka.javasdk.view.TableUpdater;
import akka.javasdk.annotations.DeleteHandler;
import customer.domain.Customer;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

@ComponentId("summary_customer_by_name")
public class CustomerSummaryByName extends View {

  public record CustomerSummary(String id, String name) { }

  // tag::delete[]
  @Consume.FromKeyValueEntity(value = CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> { // <1>

    // ...
    @DeleteHandler // <2>
    public Effect<Customer> onDelete() {
      return effects().deleteRow(); // <3>
    }
  }
  // end::delete[]

  // tag::projection[]
  @Query("SELECT customerId AS id, name FROM customers WHERE name = :customerName") // <1>
  public QueryEffect<CustomerSummary> getCustomer(String customerName) {
    return queryResult(); // <2>
  }
  // end::projection[]

}
