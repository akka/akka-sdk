package customer.application;

import akka.javasdk.view.TableUpdater;
import customer.domain.Customer;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

import java.util.List;

@ComponentId("customers_by_city")
// tag::view-test[]
public class CustomersByCity extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> {}

  @Query("""
    SELECT * AS customers
        FROM customers_by_city
      WHERE address.city = ANY(:cities)
    """)
  public QueryEffect<CustomerList> getCustomers(List<String> cities) {
    return queryResult();
  }

  // tag::stream[]
  @Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city")
  public QueryStreamEffect<Customer> streamCustomersInCity(String city) {
    return queryStreamResult();
  }
  // end::stream[]

  // tag::continuous-stream[]
  @Query(value = "SELECT * FROM customers_by_city WHERE address.city = :city", streamUpdates = true)
  public QueryStreamEffect<Customer> continuousCustomersInCity(String city) {
    return queryStreamResult();
  }
  // end::continuous-stream[]


}
// end::view-test[]
