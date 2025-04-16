# Invoking Akka services

For security reasons, _Services_ deployed to Akka have limited exposure by default. You can invoke services that have been deployed to Akka in the following ways:

* From another Akka service in the same project
* By [exposing services to the internet](#_exposing_services_to_the_internet) using routes
* [During testing and development](#_testing_and_development) from the console or using the `akka` proxy command

## Exposing services to the internet

Akka allows you to expose your services to the internet, using routes.

A route declares how traffic to a particular hostname gets routed to your services. You can let Akka generate a hostname for you, or you can provide your own hostname at your own domain. If you want to use your own hostname, you’ll need to register your domain and configure your Domain Name System (DNS) settings using a third party DNS service.

All traffic to Akka uses Transport Layer Security (TLS). Akka will automatically provision a certificate for you using [Let’s Encrypt, window="new"](https://letsencrypt.org/). The certificate is provisioned whether you use an Akka provided hostname, or bring your own.

**📌 NOTE**\
Routes are global by default and will be replicated over to a new region if/when the project gets to be multi-region, unless created with flag `--region [region]` or converted to regional after the fact. Refer to [Managing resources in multi-region](operations:regions/index.adoc#multi-region-resources) for more information.

### Exposing a single service

If you have a single service you want to expose to the internet using an Akka generated hostname, you can do so using the `akka service expose` command. This command is provided as a convenience, particularly when you’re getting started with Akka:

```bash
$ akka service expose my-service
Service 'my-service' was successfully exposed at: spring-tooth-3406.us-east1.akka.app
```

You can now access your service using the hostname described in the output of the command above. For example, if creating an HTTP client for the above service, you need to configure it to send requests to `spring-tooth-3406.us-east1.akka.app:443`.

You also have the option of enabling Cross-Origin Resource Sharing (CORS) for the service, using the `--enable-cors` flag.

### Managing routes

Routes give you the ability to direct incoming traffic to a single hostname to more than one service. To manage this, you can use the `akka routes` command. Before you create a route, you need to provision a hostname.

#### Provisioning a custom hostname

If you want to provision a custom hostname, you first need to register it with your project. Only one project can use a given hostname. Let’s say the hostname you want to register is called `ecommerce.acme.org`. To register a hostname, use the `akka project hostname add` command:

```bash
$ akka project hostname add ecommerce.acme.org
HOSTNAME                      GENERATED   REGION     CNAME
ecommerce.acme.org            false       us-east1   us-east1.akka.app
```

Notice the CNAME value above, this tells you what you need to point your hostname to. You will need to create a `CNAME` record for `ecommerce.acme.org` that points to `us-east1.akka.app` with your DNS provider. Akka will not provision any routes for this hostname until your DNS configuration is correct. Note, it can take up to 24 hours for DNS changes to take effect, depending on your DNS provider.

#### Provisioning a generated hostname

If you do not want to bring your own hostname, you can let Akka generate one for you, by running `akka project hostname add` with no arguments:

```bash
$ akka project hostname add
HOSTNAME                                      GENERATED   REGION     CNAME
young-fire-2481.us-east1.akka.app             true        us-east1
```

This shows the hostname that was just generated for you. You will need this when you create your route.

#### Creating routes

Let’s assume you want to expose two HTTP services:

1. `shopping-cart`, which has an HTTP Endpoint with path `/carts`
2. `product-info`, which has an HTTP Endpoint with path `/product`

Let’s also assume that you want to name the route `acme-ecommerce`, and that the hostname you want to serve it at is `ecommerce.acme.org`, and that you’ve already added this hostname to the project following the instructions above.

We can now create the route:

```bash
akka route create acme-ecommerce \
  --hostname ecommerce.acme.org \
  --path /carts=shopping-cart \
  --path /product=product-info
```

Having created it, we can now get its status by listing all routes:

```bash
$ akka route list
NAME             HOSTNAME             PATHS                     CORS ENABLED   STATUS
acme-ecommerce   ecommerce.acme.org   /carts,/product           false          DnsNotVerified
```

Note above that the status is `DnsNotVerified`. This indicates that the DNS configuration for our custom hostname is not correct. More details can be obtained by getting details for the route:

```bash
$ akka route get acme-ecommerce
Route: 	acme-ecommerce
Host: 	ecommerce.acme.org

Paths:
         /carts                            shopping-cart
         /product                          product-info

Status:
	HostValidation: False
		Last Transition: 	Tue Nov  9 16:45:53 2021
		Reason: 	DnsNotVerified
		Message: 	Host ecommerce.acme.org did not resolve to a CNAME record. It must be configured to be a CNAME record to us-east1.akka.app
	Ready: False
		Last Transition: 	Tue Nov  9 16:45:53 2021
		Reason: 	Validating
		Message: 	Validating hostname
```

Here you can see the exact error message - your hostname is not resolving. To rectify this, you would need to go to your DNS provider and update the DNS record for the host, and then wait for that change to propagate. Once the problem is fixed, there is nothing you need to do, Akka will periodically recheck the DNS configuration to see if it’s updated, typically every 20 minutes. Note that DNS configuration can be cached in DNS servers for up to 24 hours, so it may take that long before Akka can see your changes.

#### Updating routes

Routes can be updated using the `akka route update` command. For example, to remove the product info service from the route, and also add an inventory service, you might run:

```bash
akka route update acme-ecommerce \
  --remove-path /product \
  --path /inventory=inventory
```

#### Working with route descriptors

You may want to specify your routes using a descriptor. Descriptors can be checked into source control, allowing you to version your routing configuration. This can be useful if you have complex routes.

You can export an existing route using the `akka route export` command, this will output the descriptor in YAML format:

```bash
$ akka route export acme-ecommerce
corsPolicy:
  allowMethods:
  - GET
  - POST
  allowOrigins:
  - https://www.acme.org
host: ecommerce.acme.org
routes:
- prefix: /carts
  route:
    service: shopping-cart
- prefix: /product
  route:
    service: product-info
```

To save the output of the command to a YAML file, you can run the command below:

```bash
akka route export acme-ecommerce > acme-commerce-routes.yaml
```

To use this descriptor to either create or update a route, you can pass the file name using the `-f` flag:

```bash
akka route update acme-ecommerce -f acme-ecommerce-route.yaml
```

You can also edit the route descriptor in place using the `akka route edit` command. This will open the route descriptor in the editor configured in the `EDITOR` environment variable, allowing you to edit and save it. On exiting the editor, the route will be updated:

```bash
$ akka route edit acme-ecommerce
Route updated.
```

For a complete reference for the Akka route descriptor, see the [Akka route descriptor reference](reference:descriptors/route-descriptor.adoc).

#### Enabling CORS

CORS can be enabled by configuring at least one allowed origin, for example:

```bash
akka route update acme-ecommerce --cors-origin https://www.acme.org --cors-method GET --cors-method POST
```

#### Securing routes

All routes are served with TLS certificates. In addition, you can also enable client certificate authentication, also known as Mutual TLS (mTLS), and customise the certificates it is provisioned with. Instructions for doing this can be found in [security:tls-certificates.adoc](security:tls-certificates.adoc).

#### HTTP Basic authentication

It’s also possible to enable HTTP Basic authentication on routes. HTTP Basic authentication requires configuring a realm, which is returned in the `WWW-Authenticate` header when authentication fails or no authentication headers are present. The realm name can be anything.

Usernames and hashed passwords are stored directly in the route descriptor. Passwords can be hashed with either sha256, sha384 or sha512. Because the passwords are hashed without salt and use a computationally cheap algorithm, only strong, randomly generated passwords should be configured, to prevent brute force, password reuse or rainbow table based attacks, should the hashes be leaked. One way to generate such a password is to use OpenSSL, for example this will generate a strong password with 128 bits of entropy:

```shellscript
openssl rand -base64 16
```

HTTP Basic authentication can be configured either with CLI arguments, or directly in the route descriptor, though editing the route descriptor is a little more powerful.

* **CLI with command line arguments**

  The HTTP realm name can be set using `--http-basic-realm`. A username/password can be added using the `--http-basic-credentials` flag, passing the username and password, separated by an `=` sign. Multiple `--http-basic-credentials` flags can be passed to configure multiple username/password pairs. Passwords should be passed in plaintext, and will be SHA256 hashed by the `akka` command before saving.

  These flags can be passed both to the `akka route create` and akka route update` commands, for example:
  ```shellscript
  akka route update my-route --http-basic-realm "My Realm" \
    --http-basic-credentials "admin=correct horse battery staple"
  ```
* **CLI with a descriptor**

  Using either the `akka route edit` command or updating the route descriptor:

  ```
  host: ecommerce.acme.org
  validation:
    httpBasic:
      realm: "My Realm"
      passwordHashes:
        admin: "sha256:xLvLH77JnWW/WdhcjLYu4tuWPw/hBvSD2a+nO9Tjmoo="
        support: "sha256:eiwSRduQVGX/XHmH00+GT8Dt/X13173SqVDCc8mNJZg="
  routes:
  - prefix: /
    route:
      service: shopping-cart
  ```
  This configures two username/passwords, one for a `admin` user and one for a `support` user. The hash value must be in the format:
  ```text
  <hash algorithm>:<base64 hash>
  ```

  Valid hash algorithms are:

  * `sha256`
  * `sha384`
  * `sha512`

  To generate such a hash value, the following OpenSSL command can be run:

  ```shellscript
  echo -n "correct horse battery staple" |
    openssl dgst -sha256 -binary |
    openssl base64 -e -A
  ```

## Testing and development

During testing and development, you can use the `akka` proxy command to invoke your services.

### `akka` proxy command

The `akka` proxy command starts a proxy running locally on your machine that forwards all requests it receives to your service, via a mechanism that authenticates you as having access to manage the service, so that you can access it without exposing it to the internet. This can be used for ad-hoc invocations of your service for testing and debugging purposes, as well as by other services running locally on your machine for integration testing purposes.

Do not use the `akka` proxy command as a mechanism for tunneling requests from other systems. Invocations through the proxy are subject to quotas that, when exceeded, could temporarily block you from being able to manage your project. This feature is only intended for testing and development.

#### Starting the proxy

The proxy can be started by running the following:

```bash
akka service proxy my-service
```

This will start the proxy on port 8080 bound to localhost. The proxy runs in the foreground, and will log the requests made through it as it receives them. You can stop the proxy by hitting Ctrl+c.

You can customize the port and bind address by running:

```bash
akka service proxy my-service --port 8081 --bind-address 0.0.0.0
```

You can now invoke your services using an HTTP client. TLS is not enabled. For example, using `curl` to invoke your services from another terminal window:

```bash
curl localhost:8081/carts/123
```

## See also

* [`akka routes` commands](reference:cli/akka-cli/akka_routes.adoc#_see_also)
* [`akka services` commands](reference:cli/akka-cli/akka_services.adoc#_see_also)
