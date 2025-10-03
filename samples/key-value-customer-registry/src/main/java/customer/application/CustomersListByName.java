package customer.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;

@Component(id = "customers-list-by-name")
// tag::class[]
public class CustomersListByName extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<Customer> {} // <1>

  @Query(
    """
    SELECT * AS customers
      FROM customers_by_name
      WHERE name = :name
    """
  ) // <2>
  public QueryEffect<CustomerList> getCustomers(String name) { // <3>
    return queryResult();
  }
}
// end::class[]
