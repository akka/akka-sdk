# TLS certificates

When exposing Akka [services to the internet](operations:services/invoke-service.adoc#exposing-internet), Transport Layer Security (TLS) is used to secure all requests. By default, Akka provisions a server certificate via [Let’s Encrypt, window="new"](https://letsencrypt.org), which encrypts connections and ensures the client can verify the server’s identity. However, Akka also allows for advanced TLS configurations, including the use of custom server certificates and client certificates for additional security.

## Why Use TLS?
TLS helps achieve two essential security goals:

* **Server Authentication**: Clients can trust that they are connecting to the intended server.
* **Data Encryption**: TLS ensures that all communication is encrypted and protected from tampering.

By default, Akka’s setup does not authenticate the client’s identity, which means any internet client can connect to exposed services. To further secure access, Akka supports Mutual TLS (mTLS), requiring clients to present valid certificates.

## Client Certificates (Mutual TLS)
Client certificates (mTLS) allow you to require clients to present a trusted certificate to connect, offering a secure and coarse-grained level of authentication. This setup is ideal when you want to restrict access to services you control.

* **Certificate Authority (CA)**: To enable client certificates, you need a CA capable of issuing certificates to trusted clients. Many organizations use solutions like [HashiCorp Vault, window="new"](https://www.vaultproject.io/) or [Kubernetes cert-manager](https://cert-manager.io). If you already have a CA, you’ll only need its certificate to configure client certificate validation in Akka.

### Creating your own CA

If you don’t have an existing CA, you can create one using the [smallstep CLI, window="new"](https://smallstep.com/cli/):

1. **Install smallstep CLI** (installation instructions available at [smallstep, window="new"](https://smallstep.com/cli/)).
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

### Configuring client CA in Akka

Now that we have a CA certificate, we can configure it as a secret in Akka.

1. **Create a CA secret**: To use your CA certificate in Akka, configure it as a TLS CA secret:

   ```shell
   akka secret create tls-ca my-root-ca --cert ./my-root-ca.crt
   ```
2. **Configure a route to use the CA secret**:

   We now need to configure a route to use a secret. Routes can be created by following the instructions in [exposing services to the internet](operations:services/invoke-service.adoc#exposing-internet).
   * If you haven’t created your route, use the following command to create it with mTLS enabled:

     ```shell
     akka route create my-route --client-ca-secret my-root-ca
     ```
   * To update an existing route:

     ```shell
     akka route update my-route --client-ca-secret my-root-ca
     ```

### Configuring a client CA via the Route Descriptor

For more consistent management of routes, use the [Route Descriptor](reference:descriptors/route-descriptor.adoc) to control all details of the route in one place.

* Using either the `akka route edit` command or updating the route descriptor:

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

## Testing mTLS setup

Once configured, you can test the setup with curl. Here’s an example:

* **Without Client Certificate**: the connection fails

  Your service should now be secured. You can test that it’s secured using curl. Let’s say the URL that your service is exposed on is `spring-tooth-3406.us-east1.akka.services`. Try issuing a simple curl request on it:

  ```
  $ curl https://spring-tooth-3406.us-east1.akka.services -I
  curl: (56) OpenSSL SSL_read: error:1409445C:SSL routines:ssl3_read_bytes:tlsv13 alert certificate required, errno 0
  ```
  This should return an error indicating that a client certificate is required.
* **With client certificate**:

  ```
  $ curl https://spring-tooth-3406.us-east1.akka.services -I --key my-client.key --cert my-client.crt
  HTTP/2 404
  content-length: 0
  date: Wed, 10 Nov 2021 05:00:59 GMT
  server: envoy
  x-envoy-upstream-service-time: 19
  ```

  If configured correctly, this will establish a secure connection.

## Client certificate validation

You can add further restrictions by validating specific details on the client certificate’s subject. The client certificate’s subject can either be the _Common_ _Name_ (CN) in the _Subject_ field of the certificate, or a _DNS Subject Alternative Name_ in the certificate. Use command line arguments or route descriptors to specify subject validation:

* **Example command**:

  ```shellscript
  akka route update my-route --client-certificate-subject client.acme.org
  ```

  You can match multiple subject names by adding multiple --client-certificate-subject arguments or using wildcards (*). For instance:

  ```shellscript
  akka route update my-route --client-certificate-subject *.acme.org
  ```

### Configuring certificate validation using the Route Descriptor

For more consistent management of routes, use the [Route Descriptor](reference:descriptors/route-descriptor.adoc) to control all details of the route in one place.

* Using either the `akka route edit` command or updating the route descriptor:

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
* **Multiple subject matchers** can be defined. `hasPrefix`, `hasSuffix` and `regex` can also be used to match the subject. For example, this will allow any certificate subject name under the domain `acme.org`:

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

## Custom server certificates

While Let’s Encrypt automatically provides server certificates, you may wish to use a custom server certificate in certain situations:

* **CA Authorization**: Your domain’s Certification Authority Authorization (CAA) policy doesn’t permit Let’s Encrypt.
* **Non-Public Certificates**: You may prefer certificates trusted only by your internal clients and servers.

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

### Configuring custom certificates with the Route Descriptor

Use the [Route Descriptor](reference:descriptors/route-descriptor.adoc) to control all details of the route in one place.

* Using either the `akka route edit` command or updating the route descriptor:

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

## See also
* [`akka secret create` commands](reference:cli/akka-cli/akka_secrets_create.adoc)
* [`akka routes` commands](reference:cli/akka-cli/akka_routes.adoc)
* [reference:descriptors/route-descriptor.adoc](reference:descriptors/route-descriptor.adoc)
