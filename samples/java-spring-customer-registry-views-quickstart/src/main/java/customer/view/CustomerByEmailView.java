package customer.view;

// tag::class[]
import customer.domain.Customer;
import customer.api.CustomerEntity;
import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Consume;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;

@ViewId("view_customers_by_email") // <1>
@Table("customers_by_email") // <2>
@Consume.FromValueEntity(CustomerEntity.class)// <3>
public class CustomerByEmailView extends View<Customer> { //  <4>

  public record QueryParameters(String email) {
  }
  @Query("SELECT * FROM customers_by_email WHERE email = :email") // <5>
  public Customer getCustomer(QueryParameters params) {
    return null; // <6>
  }
}
// end::class[]