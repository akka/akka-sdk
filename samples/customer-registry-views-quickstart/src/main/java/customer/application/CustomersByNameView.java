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

  // tag::row[]
  public record CustomerSummary(String customerId, String name, String email) { }
  // end::row[]

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomerByNameUpdater extends TableUpdater<CustomerSummary> { // <1>
    public Effect<CustomerSummary> onUpdate(Customer customer) { // <2>
      return effects()
          .updateRow(new CustomerSummary(customer.customerId(), customer.name(), customer.email()));
    }
  }

  @Query("SELECT * FROM customers_by_name WHERE name = :name") // <3>
  public QueryEffect<CustomerSummary> getFirstCustomerSummary(String name) { // <4>
    return queryResult();
  }
  // end::class[]

  public record Customers(Collection<CustomerSummary> customers) { } // <6>

  @Query("SELECT * AS customers FROM customers_by_name WHERE name = :name") // <7>
  public QueryEffect<Customers> getCustomers(String name) {
    return queryResult(); // <8>
  }

  // tag::stream[]
  @Query("SELECT * FROM customers_by_name WHERE name = :name")
  public QueryStreamEffect<CustomerSummary> getCustomerSummaryStream(String name) {
    return queryStreamResult();
  }
  // end::stream[]

  // tag::continuous-stream[]
  @Query(value = "SELECT * FROM customers_by_name WHERE name = :name", streamUpdates = true)
  public QueryStreamEffect<CustomerSummary> continuousGetCustomerSummaryStream(String name) {
    return queryStreamResult();
  }
  // end::continuous-stream[]
// tag::class[]
}
// end::class[]
