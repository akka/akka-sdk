package customer.view;

import akka.platform.javasdk.view.TableUpdater;
import customer.api.CustomerEntity;
import customer.api.CustomerList;
import customer.domain.Customer;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

@ComponentId("customers_by_name")
// tag::class[]
public class CustomersResponseByName extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> { }

  public record QueryParameters(String name) { }

  @Query("""
    SELECT * AS customers
      FROM customers_by_name
      WHERE name = :name
    """) // <2>
  public QueryEffect<CustomerList> getCustomers(QueryParameters params) { // <4>
    return queryResult();
  }
}
// end::class[]
