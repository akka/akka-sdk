package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerList;
import customer.domain.Customer;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ViewId;
import akka.platform.javasdk.view.View;

@ViewId("customers_by_name")
@Table("customers_by_name")
// tag::class[]
@Consume.FromKeyValueEntity(CustomerEntity.class)
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
