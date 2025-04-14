package customer.application;

// tag::class[]

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;

import java.util.List;

@ComponentId("customers-by-email") // <1>
public class CustomersByEmail extends View { // <2>

  public record Customers(List<Customer> customers) { }

  @Consume.FromKeyValueEntity(CustomerEntity.class) // <3>
  public static class CustomersByEmailUpdater extends TableUpdater<Customer> { } // <4>

  @Query("SELECT * AS customers FROM customers_by_email WHERE email = :email") // <5>
  public QueryEffect<Customers> getCustomer(String email) {
    return queryResult(); // <6>
  }
}
// end::class[]
