<!-- <nav> -->
- [Akka](../index.html)
- [Operating](index.html)
- [Akka Automated Operations](akka-platform.html)
- [TLS certificates](tls-certificates.html)

<!-- </nav> -->

# TLS certificates

When exposing Akka [services to the internet](services/invoke-service.html#exposing-internet), Transport Layer Security (TLS) is used to secure all requests. By default, Akka provisions a server certificate via [Let’s Encrypt](https://letsencrypt.org/), which encrypts connections and ensures the client can verify the server’s identity. However, Akka also allows for advanced TLS configurations, including the use of custom server certificates and client certificates for additional security.

## <a href="about:blank#_why_use_tls"></a> Why Use TLS?

TLS helps achieve two essential security goals:

- **Server Authentication**: Clients can trust that they are connecting to the intended server.
- **Data Encryption**: TLS ensures that all communication is encrypted and protected from tampering.
By default, Akka’s setup does not authenticate the client’s identity, which means any internet client can connect to exposed services. To further secure access, Akka supports Mutual TLS (mTLS), requiring clients to present valid certificates.

## <a href="about:blank#_client_certificates_mutual_tls"></a> Client Certificates (Mutual TLS)

Client certificates (mTLS) allow you to require clients to present a trusted certificate to connect, offering a secure and coarse-grained level of authentication. This setup is ideal when you want to restrict access to services you control.

- **Certificate Authority (CA)**: To enable client certificates, you need a CA capable of issuing certificates to trusted clients. Many organizations use solutions like [HashiCorp Vault](https://www.vaultproject.io/) or [Kubernetes cert-manager](https://cert-manager.io/). If you already have a CA, you’ll only need its certificate to configure client certificate validation in Akka.

### <a href="about:blank#_creating_your_own_ca"></a> Creating your own CA

If you don’t have an existing CA, you can create one using the [smallstep CLI](https://smallstep.com/cli/):

1. **Install smallstep CLI** (installation instructions available at [smallstep](https://smallstep.com/cli/)).
2. Create a CA certificate and key:

```shell
step certificate create --profile root-ca rootca.acme.org \
  my-root-ca.crt my-root-ca.key --insecure --no-password
```
3. **Generate a client certificate** (used by clients connecting to your service):

```shell
step certificate create client.acme.org my-client.crt my-client.key \
  --ca my-root-ca.crt --ca-key my-root-ca.key --insecure --no-password
```

### <a href="about:blank#_configuring_client_ca_in_akka"></a> Configuring client CA in Akka

Now that we have a CA certificate, we can configure it as a secret in Akka.

1. **Create a CA secret**: To use your CA certificate in Akka, configure it as a TLS CA secret:

```shell
akka secret create tls-ca my-root-ca --cert ./my-root-ca.crt
```
2. **Configure a route to use the CA secret**:

We now need to configure a route to use a secret. Routes can be created by following the instructions in [exposing services to the internet](services/invoke-service.html#exposing-internet).

  - If you haven’t created your route, use the following command to create it with mTLS enabled:

```shell
akka route create my-route --client-ca-secret my-root-ca
```
  - To update an existing route:

```shell
akka route update my-route --client-ca-secret my-root-ca
```

### <a href="about:blank#_configuring_a_client_ca_via_the_route_descriptor"></a> Configuring a client CA via the Route Descriptor

For more consistent management of routes, use the [Route Descriptor](../reference/descriptors/route-descriptor.html) to control all details of the route in one place.

- Using either the `akka route edit` command or updating the route descriptor:

```yaml
host: ecommerce.acme.org
tls:
  clientValidationCa:
    name: my-root-ca
routes:
- prefix: /
  route:
    service: shopping-cart
```

## <a href="about:blank#_testing_mtls_setup"></a> Testing mTLS setup

Once configured, you can test the setup with curl. Here’s an example:

- **Without Client Certificate**: the connection fails

Your service should now be secured. You can test that it’s secured using curl. Let’s say the URL that your service is exposed on is `spring-tooth-3406.us-east1.akka.services`. Try issuing a simple curl request on it:

```none
$ curl https://spring-tooth-3406.us-east1.akka.services -I
curl: // (56) OpenSSL SSL_read: error:1409445C:SSL routines:ssl3_read_bytes:tlsv13 alert certificate required, errno 0
```
This should return an error indicating that a client certificate is required.
- **With client certificate**:

```none
$ curl https://spring-tooth-3406.us-east1.akka.services -I --key my-client.key --cert my-client.crt
HTTP/2 404
content-length: 0
date: Wed, 10 Nov 2021 05:00:59 GMT
server: envoy
x-envoy-upstream-service-time: 19
```
If configured correctly, this will establish a secure connection.

## <a href="about:blank#_client_certificate_validation"></a> Client certificate validation

You can add further restrictions by validating specific details on the client certificate’s subject. The client certificate’s subject can either be the *Common* *Name* (CN) in the *Subject* field of the certificate, or a *DNS Subject Alternative Name* in the certificate. Use command line arguments or route descriptors to specify subject validation:

- **Example command**:

```shellscript
akka route update my-route --client-certificate-subject client.acme.org
```
You can match multiple subject names by adding multiple --client-certificate-subject arguments or using wildcards (*). For instance:

```shellscript
akka route update my-route --client-certificate-subject *.acme.org
```

### <a href="about:blank#_configuring_certificate_validation_using_the_route_descriptor"></a> Configuring certificate validation using the Route Descriptor

For more consistent management of routes, use the [Route Descriptor](../reference/descriptors/route-descriptor.html) to control all details of the route in one place.

- Using either the `akka route edit` command or updating the route descriptor:

```yaml
host: ecommerce.acme.org
tls:
  clientValidationCa:
    name: my-root-ca
validation:
  clientCertificate:
    subjectMatches:
    - exact: client.acme.org
routes:
- prefix: /
  route:
    service: shopping-cart
```
- **Multiple subject matchers** can be defined. `hasPrefix`, `hasSuffix` and `regex` can also be used to match the subject. For example, this will allow any certificate subject name under the domain `acme.org`:

```yaml
host: ecommerce.acme.org
tls:
  clientValidationCa:
    name: my-root-ca
validation:
  clientCertificate:
    subjectMatches:
    - hasSuffix: .acme.org
routes:
- prefix: /
  route:
    service: shopping-cart
```

## <a href="about:blank#_custom_server_certificates"></a> Custom server certificates

While Let’s Encrypt automatically provides server certificates, you may wish to use a custom server certificate in certain situations:

- **CA Authorization**: Your domain’s Certification Authority Authorization (CAA) policy doesn’t permit Let’s Encrypt.
- **Non-Public Certificates**: You may prefer certificates trusted only by your internal clients and servers.
To configure a custom TLS secret:

1. **Prepare Key and Certificate**: Ensure your server’s key and certificate are in unencrypted PEM format.
2. Create the TLS secret:

```shell
akka secret create tls my-tls-cert --key ./my-key.pem --cert ./my-cert.pem
```
3. Add the TLS secret to a route:

```shell
akka route update my-route --server-certificate-secret my-tls-cert
```

### <a href="about:blank#_configuring_custom_certificates_with_the_route_descriptor"></a> Configuring custom certificates with the Route Descriptor

Use the [Route Descriptor](../reference/descriptors/route-descriptor.html) to control all details of the route in one place.

- Using either the `akka route edit` command or updating the route descriptor:

```yaml
host: ecommerce.acme.org
tls:
  serverCertificate:
    name: my-tls-cert
routes:
- prefix: /
  route:
    service: shopping-cart
```

## <a href="about:blank#_see_also"></a> See also

- <a href="../reference/cli/akka-cli/akka_secrets_create.html">`akka secret create` commands</a>
- <a href="../reference/cli/akka-cli/akka_routes.html">`akka routes` commands</a>
- [Route Descriptor reference](../reference/descriptors/route-descriptor.html)

<!-- <footer> -->
<!-- <nav> -->
[Regions](regions/index.html) [Observability and monitoring](observability-and-monitoring/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->