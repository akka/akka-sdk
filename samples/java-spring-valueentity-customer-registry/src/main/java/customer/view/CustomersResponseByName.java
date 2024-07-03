package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerList;
import customer.domain.Customer;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Consume;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("customers_by_name")
@Table("customers_by_name")
// tag::class[]
@Consume.FromValueEntity(CustomerEntity.class)
public class CustomersResponseByName extends View<Customer> {

  public record QueryParameters(String name) { }

  @Query("""
    SELECT * AS customers
      FROM customers_by_name
      WHERE name = :name
    """) // <2>
  public CustomerList getCustomers(QueryParameters params) { // <4>
    return null;
  }
}
// end::class[]
