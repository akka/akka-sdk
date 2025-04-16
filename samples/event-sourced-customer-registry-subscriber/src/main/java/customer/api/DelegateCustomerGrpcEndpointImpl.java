package customer.api;

import akka.grpc.GrpcServiceException;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import customer.api.proto.CreateCustomerRequest;
import customer.api.proto.CreateCustomerResponse;
import customer.api.proto.CustomerGrpcEndpointClient;
import customer.api.proto.DelegateCustomerGrpcEndpoint;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::delegate[]
@GrpcEndpoint
public class DelegateCustomerGrpcEndpointImpl implements DelegateCustomerGrpcEndpoint {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private CustomerGrpcEndpointClient customerService;

  public DelegateCustomerGrpcEndpointImpl(GrpcClientProvider clientProvider) { // <1>
    customerService = clientProvider.grpcClientFor(CustomerGrpcEndpointClient.class, "customer-registry"); // <2>
  }

  @Override
  public CreateCustomerResponse createCustomer(CreateCustomerRequest in) {
    log.info("Delegating customer creation to upstream gRPC service: {}", in);
    if (in.getCustomerId().isEmpty())
      throw new GrpcServiceException(Status.INVALID_ARGUMENT.augmentDescription("No id specified"));

    try {
      return customerService
          .createCustomer(in); // <3>

    } catch (Exception ex) {
      throw new RuntimeException("Delegate call to create upstream customer failed", ex);
    }
  }
}
// end::delegate[]
