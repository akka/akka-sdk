package customer.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.Done;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import customer.api.CustomerRegistryEndpoint.CreateCustomerRequest;
import customer.api.proto.ChangeAddressRequest;
import customer.api.proto.ChangeAddressResponse;
import customer.api.proto.ChangeNameRequest;
import customer.api.proto.ChangeNameResponse;
import customer.api.proto.CreateCustomerResponse;
import customer.api.proto.Customer;
import customer.api.proto.CustomerByEmailRequest;
import customer.api.proto.CustomerByNameRequest;
import customer.api.proto.CustomerGrpcEndpointClient;
import customer.api.proto.CustomerList;
import customer.api.proto.CustomerSummary;
import customer.api.proto.DelegateCustomerGrpcEndpointClient;
import customer.api.proto.GetCustomerRequest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * This test mocks out the upstream "customer-registry" service per test case so the local endpoint
 * and delegate can be exercised without a real upstream service running.
 *
 * <p>For a test of the full cross-service subscription flow (which does require the real upstream
 * service running on :9000), see {@link CustomerIntegrationTest}.
 */
public class CustomerRegistryEndpointMockedTest extends CustomerRegistryIntegrationTest {

  @AfterEach
  public void resetMocks() {
    testKit.getMockedHttpServices().reset();
    testKit.getMockedGrpcServices().reset();
  }

  @Test
  public void httpEndpointReturnsCreatedWhenUpstreamReturnsCreated() {
    testKit
      .getMockedHttpServices()
      .mockResponse(
        "customer-registry",
        request -> HttpResponse.create().withStatus(StatusCodes.CREATED)
      );

    var request = new CreateCustomerRequest(
      "foo@example.com",
      "Johanna",
      new CustomerRegistryEndpoint.Address("street", "city")
    );

    var response = httpClient
      .POST("/customer/" + UUID.randomUUID())
      .withRequestBody(request)
      .invoke();

    assertThat(response.httpResponse().status()).isEqualTo(StatusCodes.CREATED);
  }

  @Test
  public void httpEndpointFailsWhenUpstreamReturnsError() {
    testKit
      .getMockedHttpServices()
      .mockResponse(
        "customer-registry",
        request -> HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR)
      );

    var request = new CreateCustomerRequest(
      "bar@example.com",
      "Bjorn",
      new CustomerRegistryEndpoint.Address("street", "city")
    );

    var response = httpClient
      .POST("/customer/" + UUID.randomUUID())
      .withRequestBody(request)
      .invoke();

    assertThat(response.httpResponse().status()).isEqualTo(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  @Test
  public void grpcDelegateReturnsResponseFromUpstream() {
    testKit
      .getMockedGrpcServices()
      .mockResponse(
        "customer-registry",
        CustomerGrpcEndpointClient.class,
        new CustomerGrpcEndpointMock()
      );

    var delegateClient = getGrpcEndpointClient(DelegateCustomerGrpcEndpointClient.class);
    var request = customer.api.proto.CreateCustomerRequest.newBuilder()
      .setCustomerId(UUID.randomUUID().toString())
      .setCustomer(Customer.newBuilder().setName("Carla").build())
      .build();

    var response = delegateClient.createCustomer(request);

    assertThat(response).isNotNull();
  }

  /**
   * Mock implementation — returns a fixed empty response for createCustomer; every other method is
   * not exercised by this test and throws if called so test breakage is loud.
   */
  static final class CustomerGrpcEndpointMock extends CustomerGrpcEndpointClient {

    @Override
    public CreateCustomerResponse createCustomer(
      customer.api.proto.CreateCustomerRequest in
    ) {
      return CreateCustomerResponse.newBuilder().build();
    }

    @Override
    public Customer getCustomer(GetCustomerRequest in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeNameResponse changeName(ChangeNameRequest in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeAddressResponse changeAddress(ChangeAddressRequest in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CustomerList customerByName(CustomerByNameRequest in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CustomerList customerByEmail(CustomerByEmailRequest in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public akka.stream.javadsl.Source<CustomerSummary, akka.NotUsed> customerByEmailStream(
      CustomerByEmailRequest in
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Done> close() {
      return CompletableFuture.completedFuture(Done.getInstance());
    }

    @Override
    public CompletionStage<Done> closed() {
      return new CompletableFuture<>();
    }
  }
}
