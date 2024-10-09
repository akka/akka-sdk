package customer.application;

// tag::class[]
import akka.javasdk.view.TableUpdater;
import customer.domain.Customer;
import akka.javasdk.view.View;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;

import java.util.List;

@ComponentId("view_customers_by_email") // <1>
public class CustomersByEmailView extends View { // <2>

  public record Customers(List<Customer> customers) { }

  @Consume.FromKeyValueEntity(CustomerEntity.class) // <3>
  public static class CustomerByEmail extends TableUpdater<Customer> { } // <4>

  @Query("SELECT * AS customers FROM customers_by_email WHERE email = :email") // <5>
  public QueryEffect<Customers> getCustomer(String email) {
    return queryResult(); // <6>
  }
}
// end::class[]