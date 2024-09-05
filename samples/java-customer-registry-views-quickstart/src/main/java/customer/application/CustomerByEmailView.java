package customer.application;

// tag::class[]
import akka.javasdk.view.TableUpdater;
import customer.domain.Customer;
import akka.javasdk.view.View;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;

@ComponentId("view_customers_by_email") // <1>
public class CustomerByEmailView extends View { //  <3>

  @Consume.FromKeyValueEntity(CustomerEntity.class)// <2>
  public static class CustomersByEmail extends TableUpdater<Customer> { }

  @Query("SELECT * FROM customers_by_email WHERE email = :email") // <4>
  public QueryEffect<Customer> getCustomer(String email) {
    return queryResult(); // <5>
  }
}
// end::class[]