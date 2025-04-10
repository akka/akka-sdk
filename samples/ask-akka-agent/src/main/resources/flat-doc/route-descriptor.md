# Route Descriptor reference

## Akka Route descriptor

An Akka route describes the way ingress traffic is routed to your Akka services. It is used by the `akka route update` command, described in [working with route descriptors](operations:services/invoke-service.adoc#descriptor).

| Field | Type | Description |
| --- | --- | --- |
| **host** | string _required_ | The host that this route is for. This hostname must be configured as one of the project hostnames. |
| **routes** | [][RouteRule](#routerule) _required_ | The routing rules defined for this route. |
| **tls** | [RouteTls](#routetls) | The TLS configuration for this route. |
| **validation** | [RouteValidation](#routevalidation) | Validation, such as authentication, for the route. |
| **corsPolicy** | [RouteCorsPolicy](#routecorspolicy) | The CORS policy for this route. If present, will enable CORS support for the route. |

### RouteRule

A route rule is a rule that defines how requests should be matched, and what the destination for those matched requests should be.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string | A name for this rule. Only used for debugging purposes. |
| **prefix** | string _required_ | The prefix to match for this route. Must start with a `/`. |
| **route** | [RouteDestination](#routedestination) _required_ | The destination for requests matched by this rule. |

### RouteDestination

A destination for a routed requests.

| Field | Type | Description |
| --- | --- | --- |
| **service** | string | The name of a service in this project that requests should be routed to |

### RouteTls

TLS configuration for a route.

| Field | Type | Description |
| --- | --- | --- |
| **clientValidationCa** | [ObjectRef](#ObjectRef) | The name of a secret of type CA that should be used to validate client certificates provided to the server. The presence of this configuration will cause any requests that do not provide a client certificate, or do not provide a certificate that is trusted by this CA, to be rejected. |
| **serverCertificate** | [ObjectRef](#ObjectRef) | The name of a secret of type TLS that should be used as the servers certificate. Allows a custom TLS certificate to be used for the host, instead of Akka’s automatic certificate provisioning from Let’s Encrypt. |

### RouteValidation

Validation rules for a route. This can be used to configure authentication and authorization rules.

| Field | Type | Description |
| --- | --- | --- |
| **clientCertificate** | [RouteValidationClientCertificate](#routevalidationclientcertificate) | Validation to be done on the client certificate. This can only be used when a `tls.clientValidationCa` certificate is configured. |
| **httpBasic** | [RouteValidationHttpBasic](#routevalidationhttpbasic) | HTTP Basic authentication configuration. |

### RouteValidationClientCertificate

Client certificate validation rules for a route.

| Field | Type | Description |
| --- | --- | --- |
| **subjectMatches** | [][StringMatcher](#stringmatcher) _required_ | A list of matchers to apply to the client certificate subject. Validation passes if at least one of the matchers matches either the Common Name (CN) in the subject on the client certificate, or one of the DNS Subject Alternative Names of the certificate. |

### StringMatcher

A string matcher. Exactly one match rule must be specified.

| Field | Type | Description |
| --- | --- | --- |
| **exact** | string | Perform an exact match on the value. |
| **hasPrefix** | string | Matches if the value has the given prefix. |
| **hasSuffix** | string | Matches if the value has the given suffix. |
| **regex** | string | Matches if the value matches the given regular expression. |

### RouteValidationHttpBasic

HTTP Basic authentication configured for a route.

| Field | Type | Description |
| --- | --- | --- |
| **realm** | string _required_ | The name of the realm to return in a `WWW-Authenticate` header when a `401 Unauthorized` response code is sent. |
| **passwordHashes** | map&lt;string, string> _required_ | A map of username to password hashes to validate the authentication against. The password hashes must be hashed in the format `<hash algorithm>:<base64 hash>`, where hash algorithm is one of: * `sha512` * `sha384` * `sha256` For example: |
```yaml
passwordHashes:
  admin: "sha256:xLvLH77JnWW/WdhcjLYu4tuWPw/hBvSD2a+nO9Tjmoo="
  support: "sha256:eiwSRduQVGX/XHmH00+GT8Dt/X13173SqVDCc8mNJZg="
` To generate a password hash in the above format, the following command can be used: |
```shellscript
$ echo -n "correct horse battery staple" \|
  openssl dgst -sha256 -binary \|
  openssl base64 -e -A
` Note that the use of unsalted, computationally cheap hash algorithms implies that only passwords that are strong and randomly generated, not reused between accounts or services, should be used. For service to service communication which this feature is intended for use by, where passwords are chosen by system operators rather than end users, this requirement can easily be implemented. |

### RouteCorsPolicy

A CORS policy to be used by a route.

| Field | Type | Description |
| --- | --- | --- |
| **allowOrigins** | []string | A list of origins to allow to make requests. |
| **allowMethods** | []string | A list of HTTP methods to allow to make requests, such as `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`. |
