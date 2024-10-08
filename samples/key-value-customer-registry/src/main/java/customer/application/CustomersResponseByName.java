package customer.application;

import akka.javasdk.view.TableUpdater;
import customer.domain.Customer;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

@ComponentId("customers_by_name")
// tag::class[]
public class CustomersResponseByName extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> { } // <1>

  @Query("""
    SELECT * AS customers
      FROM customers_by_name
      WHERE name = :name
    """) // <2>
  public QueryEffect<CustomerList> getCustomers(String name) { // <3>
    return queryResult();
  }
}
// end::class[]
