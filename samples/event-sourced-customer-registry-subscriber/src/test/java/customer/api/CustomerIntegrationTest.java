package customer.api;


import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.StrictResponse;
import akka.util.ByteString;
import customer.api.CustomerRegistryEndpoint.CreateCustomerRequest;
import customer.application.CustomersByNameView;
import customer.domain.CustomerEntry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test exercises the integration between the current service (customer-registry-subscriber) and the customer-registry service.
 * <p>
 * The subscriber service will first create a customer on customer-registry service. The customer will be streamed back
 * to the subscriber service and update its view.
 */
public class CustomerIntegrationTest extends CustomerRegistryIntegrationTest {


  // this test relies on a source Akka event-sourced-customer-registry service to which it subscribes.
  // Such service should be running on :9000 (and this service runs on 9001 in the test)
  @Test
  public void create()  {
    // start the real test now
    String id = UUID.randomUUID().toString();
    CreateCustomerRequest createRequest = new CreateCustomerRequest("foo@example.com", "Johanna", new CustomerRegistryEndpoint.Address("street", "city"));

    waitForUpstreamServiceStart();

    // call our own endpoint service (why not call the other endpoint directly here?) which will in turn call the endpoint of the other service
    // try until it succeeds
    Awaitility.await()
      .ignoreExceptions()
      .pollInterval(1, TimeUnit.SECONDS)
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        StrictResponse<ByteString> res =
          httpClient.POST("/customer/"+id)
            .withRequestBody(createRequest).invoke();

        assertThat(res.httpResponse().status()).isEqualTo(StatusCodes.CREATED);
      });


    // the view is eventually updated
    // on this service (updated via s2s streaming)
    Awaitility.await()
      .ignoreExceptions()
      .pollInterval(1, TimeUnit.SECONDS)
      .atMost(60, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var foundCustomers =
          componentClient.forView()
            .method(CustomersByNameView::findByName)
            .invoke(createRequest.name()
        ).customers().stream().map(CustomerEntry::name);

        assertThat(foundCustomers).containsExactly(createRequest.name());
      });
  }

  // create the client but only return it after verifying that service is reachable
  private void waitForUpstreamServiceStart() {
    // Auth headers will be like coming from this service
    var httpClient = testKit.getHttpClientProvider().httpClientFor("customer-registry");

    // wait until customer service is up
    try {
    Awaitility.await()
        .ignoreExceptions()
        .pollInterval(1, TimeUnit.SECONDS)
        .atMost(5, TimeUnit.MINUTES)
        .untilAsserted(() -> {
          var response = httpClient.GET("").invoke().httpResponse();
              // NOT_FOUND is a sign that the service is started and responding
            assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
        });
    } catch (Exception ex) {
      throw new RuntimeException("This test requires an external Akka service to be running on localhost:9000 but was not able to reach it.", ex);
    }
  }

}
