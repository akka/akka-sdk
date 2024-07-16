package customer.view;

// tag::class[]
import customer.domain.Customer;
import customer.api.CustomerEntity;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("view_customers_by_email") // <1>
@Table("customers_by_email") // <2>
@Consume.FromKeyValueEntity(CustomerEntity.class)// <3>
public class CustomerByEmailView extends View<Customer> { //  <4>

  public record QueryParameters(String email) {
  }
  @Query("SELECT * FROM customers_by_email WHERE email = :email") // <5>
  public Customer getCustomer(QueryParameters params) {
    return null; // <6>
  }
}
// end::class[]