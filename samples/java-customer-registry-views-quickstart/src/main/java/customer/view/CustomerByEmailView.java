package customer.view;

// tag::class[]
import akka.platform.javasdk.view.TableUpdater;
import customer.domain.Customer;
import customer.domain.CustomerEntity;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("view_customers_by_email") // <1>
public class CustomerByEmailView extends View { //  <3>

  @Consume.FromKeyValueEntity(CustomerEntity.class)// <2>
  public static class CustomersByEmail extends TableUpdater<Customer> { }

  public record QueryParameters(String email) {
  }
  @Query("SELECT * FROM customers_by_email WHERE email = :email") // <4>
  public QueryEffect<Customer> getCustomer(QueryParameters params) {
    return queryResult(); // <5>
  }
}
// end::class[]