<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Access Control List concepts](acls.html)

<!-- </nav> -->

# Access Control List concepts

Akka’s Access Control Lists (ACLs) provide a straightforward way to manage access to services within your projects, enabling fine-grained control over who or what can access each service or endpoint. ACLs allow you to define lists that specify permitted access sources, whether they are internal services or external internet clients.

## <a href="about:blank#_key_capabilities_of_acls"></a> Key Capabilities of ACLs

- **Granular Access Control**: Configure ACLs at the service or method level to specify which entities can access your service or invoke specific methods. For example, you can set up an ACL that restricts a payment initiation method to accept requests solely from the shopping cart service.
- **Internet Access Control**: Use ACLs to control which services or methods can be accessed over the internet, providing additional security for services exposed to external clients.

## <a href="about:blank#_secure_inter_service_communication_with_mutual_tls_mtls"></a> Secure Inter-Service Communication with Mutual TLS (mTLS)

In Akka, all inter-service communication within a project is secured using Mutual TLS (mTLS), which is automatically managed by Akka. This means:

- **No Additional Configuration Needed**: HTTP or gRPC clients used for calls between services do not require specific configurations for mTLS.
- **Transparent mTLS Injection**: Akka transparently captures outgoing requests and wraps them in an mTLS connection, securing communication between services.
Akka leverages mTLS to identify and authenticate services, enabling it to apply access policies based on the origin of each request.

## <a href="about:blank#_principals_in_acls"></a> Principals in ACLs

In Akka, a principal is an abstract entity that represents the origin or source of a request. Principals determine how access is granted based on the request’s source. Currently, Akka supports the following principals:

- **Service Principals**: Represent Akka services, allowing you to configure ACLs to permit access only to requests originating from specific services within the project.
- **Internet Principal**: Represents external requests coming from the internet. Akka identifies these requests as originating from the Akka ingress (according to a configured route). Note that:

  - Requests labeled with the **internet principal** are validated by mTLS, but this does not imply that mTLS was used to connect to the ingress from the internet client itself. To enforce mTLS on connections from internet clients, refer to [TLS certificates](../operations/tls-certificates.html).

## <a href="about:blank#_configuring_acls"></a> Configuring ACLs

To configure ACLs and secure your Akka services effectively, follow these steps:

1. **Identify the Service or Method**: Determine which services or methods require access restrictions. For example, a payment service might only need to accept requests from an authorized shopping cart service.
2. **Define Principals and Permissions**: Specify which principals (services or internet) are permitted to access the identified service or method.
3. **Apply ACL Policies**: Use Akka’s ACL configurations to enforce these permissions. You can configure ACLs via the Akka Console or CLI, where you can define which services can interact with others or be accessed from the internet.
For detailed configuration steps and examples, see the <a href="../java/access-control.html">Access Control Lists in the **Developing** section</a>.

## <a href="about:blank#_practical_example"></a> Practical Example

Suppose you have a payment service and wish to restrict access to only requests from the shopping cart service. You would:

1. **Define an ACL** for the payment service, allowing only the shopping cart service principal to access the payment initiation endpoint.
2. **Restrict Internet Access** by not associating the payment initiation endpoint with the internet principal, preventing direct internet access.
This setup ensures that only internal, authorized services within the project can trigger sensitive actions on the payment service.

## <a href="about:blank#_summary"></a> Summary

- **ACLs** in Akka allow you to control service and method access at a granular level.
- **mTLS** secures all inter-service communication, removing the need for additional client configurations.
- **Principals** (service and internet) enable access policies based on request origin.
- **Internet Access Control** lets you specify which services and methods are accessible from external clients.
With ACLs, you can secure your Akka environment and ensure only authorized entities access sensitive services or endpoints.

## <a href="about:blank#_see_also"></a> See also

- [TLS certificates](../operations/tls-certificates.html)
- [Access Control Lists (ACLs)](../java/access-control.html) in the **Developing** section

<!-- <footer> -->
<!-- <nav> -->
[AI Agents](ai-agents.html) [Developing](../java/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->