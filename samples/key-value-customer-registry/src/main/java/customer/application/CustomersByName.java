package customer.application;

// tag::class[]

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;

import java.util.Collection;

@ComponentId("customers_by_name")
public class CustomersByName extends View {

  // tag::row[]
  public record CustomerSummary(String customerId, String name, String email) { }
  // end::row[]

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomerByNameUpdater extends TableUpdater<CustomerSummary> { // <1>
    public Effect<CustomerSummary> onUpdate(Customer customer) { // <2>
      return effects()
          .updateRow(new CustomerSummary(updateContext().eventSubject().get(), customer.name(), customer.email())); // <3>
    }
  }

  @Query("SELECT * FROM customers_by_name WHERE name = :name") // <4>
  public QueryEffect<CustomerSummary> getFirstCustomerSummary(String name) { // <5>
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
