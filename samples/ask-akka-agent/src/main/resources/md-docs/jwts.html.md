<!-- <nav> -->
- [Akka](../index.html)
- [Reference](index.html)
- [JSON Web Tokens (JWTs)](jwts.html)

<!-- </nav> -->

# JSON Web Tokens (JWTs)

JWTs allow authentication and authorization of requests, especially for internet-facing applications and inter-service communication. JWTs provide a trusted mechanism for validating identities and permissions, ensuring secure interactions between services, users, and devices.

This section presents how you create and manage JWTs with Akka. Using JWTs to secure services is presented in [Authentication with JWTs in the Akka SDK](../sdk/auth-with-jwts.html).

## <a href="about:blank#_key_concepts_of_jwts"></a> Key Concepts of JWTs

- **Claims**: JWTs consist of claims and a signature. Claims represent user identity, roles, permissions, or access rights, allowing services to interpret what actions the JWT holder can perform.
- **Signature**: Ensures the integrity of the JWT, verifying that it was issued by a trusted entity and has not been tampered with.

### <a href="about:blank#_use_cases"></a> Use Cases

- **User and Device Authentication**: JWTs authenticate internet users or devices and can define what actions they’re authorized to perform.
- **Inter-Service Authorization**: JWTs facilitate secure serverless operations, where services validate tokens directly without needing an external verifier.

|  | Another common use case is to use claims for authorization. Imagine a social network, one of the services in that network is a friends service. When the user fetches their list of friends from the friends service, it may send them a JWT for each friend in their friends list. For each friend, the corresponding JWT will contain a claim indicating that the user is a friend of the logged in user. When the logged in user wants to send a message to another friend, they can include the JWT of that friend in their request to send a message, and the message service can verify that the JWT has the necessary friendship claim, and allow the message to be sent. |

## <a href="about:blank#_jwt_signing_approaches"></a> JWT Signing Approaches

JWTs can be signed using *symmetric* or *asymmetric* keys:

- **Symmetric Keys**: Simple setup where both issuer and verifier share a secret key. Useful in trusted environments.
- **Asymmetric Keys**: Use a private key to sign and a public key to verify. Useful when verifying parties are untrusted, as only the private key can sign tokens.

### <a href="about:blank#_supported_algorithms"></a> Supported algorithms

Akka supports the following algorithms:

| Algorithm | Description | Type |
| --- | --- | --- |
| HMD5 | HMAC with MD5 | Symmetric |
| HS224 | HMAC with SHA224 | Symmetric |
| HS256 | HMAC with SHA256 | Symmetric |
| HS384 | HMAC with SHA384 | Symmetric |
| HS512 | HMAC with SHA512 | Symmetric |
| RS256 | RSA with SHA256 | Asymmetric |
| RS384 | RSA with SHA384 | Asymmetric |
| RS512 | RSA with SHA512 | Asymmetric |
| ES256 | ECDSA with SHA256 | Asymmetric |
| ES384 | ECDSA with SHA384 | Asymmetric |
| ES512 | ECDSA with SHA512 | Asymmetric |
| EdDSA | Ed25519 | Asymmetric |

|  | Recommendation: Use HS256 for symmetric keys and ES256 for asymmetric keys. |

## <a href="about:blank#jwks"></a> Configuring JWT with JWKS keysets

JWKS (JSON Web Key Sets) is the recommended way to configure JWT validation in Akka. A keyset points to a source of public keys — such as an OpenID Connect provider, an HTTPS endpoint, or a secret — and Akka fetches and caches these keys automatically to validate incoming JWTs.

JWKS keysets are ideal for:

- Validating tokens issued by a third-party identity provider (Google, Auth0, Okta, etc.) using OIDC discovery or a JWKS URL.
- Validating tokens using keys that are rotated regularly, without redeploying the service.
- Generating and managing your own key pairs when your service needs to issue signed tokens.

### <a href="about:blank#_listing_keysets"></a> Listing keysets

To view the JWKS keysets currently configured on a service:

```shell
akka service jwks list my-service
```
Example output:

```none
ISSUER                        SOURCE
https://accounts.google.com   oidc-discovery
https://example.com           https://example.com/.well-known/jwks.json
my-issuer                     secret: my-jwks-secret
```

### <a href="about:blank#_adding_a_keyset"></a> Adding a keyset

Use `akka service jwks add` to add a keyset to a service. Exactly one source must be provided.

#### <a href="about:blank#_via_openid_connect_discovery"></a> Via OpenID Connect discovery

The simplest way to trust tokens from a standards-compliant identity provider is OIDC discovery. Akka automatically fetches the JWKS URL from the issuer’s `/.well-known/openid-configuration` endpoint.

```shell
akka service jwks add my-service \
  --oidc-discovery \
  --issuer https://accounts.google.com
```

```shell
akka service jwks add my-service \
  --oidc-discovery \
  --issuer https://my-org.auth0.com/
```
The `--issuer` flag is required when using `--oidc-discovery`. Akka validates that the OIDC discovery document is reachable and its issuer matches before adding the keyset.

#### <a href="about:blank#_via_a_jwks_url"></a> Via a JWKS URL

If your identity provider exposes a JWKS endpoint directly, you can configure it with `--jwks-url`:

```shell
akka service jwks add my-service \
  --jwks-url https://example.com/.well-known/jwks.json \
  --issuer https://example.com
```
The URL must use HTTPS. Akka validates that the endpoint is reachable and returns a valid JWKS document before adding the keyset.

#### <a href="about:blank#_from_a_secret"></a> From a secret

To validate tokens using keys stored in an Akka secret containing a JWKS JSON document:

```shell
akka service jwks add my-service \
  --secret my-jwks-secret \
  --issuer my-issuer
```
By default, the key named `jwks.json` in the secret is used. Use `--secret-key` to specify a different key name.

#### <a href="about:blank#_from_an_external_secret"></a> From an external secret

To use a JWKS document stored in an external secret provider (e.g., Azure Key Vault):

```shell
akka service jwks add my-service \
  --external-secret my-ext-secret \
  --secret-key keys.json \
  --issuer my-issuer
```

#### <a href="about:blank#_additional_options_for_add"></a> Additional options for add

| Flag | Description |
| --- | --- |
| `--allowed-algorithms` | List of algorithms permitted for this keyset (e.g. `RS256,ES256`). Restricts which signing algorithms are accepted even if the keyset itself doesn’t specify one. |
| `--refresh-interval` | How often Akka refreshes the keyset (e.g. `30m`, `2h`). Acts as an upper bound — if the source returns a shorter cache TTL, that is used instead. Defaults to `1h`. |
| `--skip-validation` | Skip the initial validation of the JWKS endpoint or OIDC discovery document. Useful when the endpoint is not yet publicly reachable at deploy time. |

### <a href="about:blank#_generating_a_keyset"></a> Generating a keyset

If your service needs to issue signed tokens itself, use `akka service jwks generate` to create an asymmetric key pair, store it as a JWKS secret, and add the keyset to the service in one step:

```shell
akka service jwks generate my-service \
  --algorithm RS256 \
  --key-id my-key-id \
  --issuer https://example.com
```

```shell
akka service jwks generate my-service \
  --algorithm ES256 \
  --key-id signing-key \
  --secret my-jwks-secret
```
The generated JWKS document (including the private key) is stored in a new secret. The service is configured to validate JWTs using the public key from that secret. If you need to issue tokens signed by the generated private key, reference the same secret from your signing service.

If `--key-id` is omitted, a random UUID is used. If `--secret` is omitted, the secret is named after the key ID.

### <a href="about:blank#_updating_a_keyset"></a> Updating a keyset

Use `akka service jwks update` to modify the configuration of an existing keyset. Identify the keyset to update using either `--issuer` (matches by issuer field) or `--index` (1-based position as shown by the list command).

```shell
akka service jwks update my-service \
  --issuer https://accounts.google.com \
  --refresh-interval 30m
```

```shell
akka service jwks update my-service \
  --index 2 \
  --allowed-algorithms RS256 --allowed-algorithms ES256
```

```shell
akka service jwks update my-service \
  --index 1 \
  --jwks-url https://example.com/new-jwks.json
```

| Flag | Description |
| --- | --- |
| `--issuer` | Identify the keyset to update by its issuer field. |
| `--index` | Identify the keyset to update by its 1-based position in the list. |
| `--new-issuer` | Set a new issuer value for the keyset. |
| `--allowed-algorithms` | Replace the list of allowed algorithms. |
| `--clear-allowed-algorithms` | Remove all algorithm restrictions from the keyset. |
| `--refresh-interval` | Set a new refresh interval (e.g. `1h`, `30m`). |
| `--clear-refresh-interval` | Reset the refresh interval to the default (1h). |
| `--jwks-url` | Update the JWKS URL (only valid for URL-backed keysets). |
| `--skip-validation` | Skip validation of the new JWKS URL. |
To change the source type (e.g. from a URL to a secret), remove the keyset and add a new one.

### <a href="about:blank#_removing_a_keyset"></a> Removing a keyset

```shell
akka service jwks remove my-service --issuer https://accounts.google.com
```

```shell
akka service jwks remove my-service --index 2
```

### <a href="about:blank#_migrating_from_jwt_keys"></a> Migrating from JWT keys

If a service was previously configured with the deprecated `akka services jwts` key approach, use `akka service jwks migrate` to convert existing keys to JWKS keysets automatically. Keys are grouped by issuer; each issuer’s keys are combined into a single JWKS secret. The old keys are removed once migration completes.

```shell
akka service jwks migrate my-service
```
Use `--secret-prefix` to customise the prefix used for the generated secret names (defaults to the service name):

```shell
akka service jwks migrate my-service --secret-prefix my-prefix
```

### <a href="about:blank#_configuring_keysets_with_a_service_descriptor"></a> Configuring keysets with a service descriptor

JWKS keysets can also be configured declaratively in a [service descriptor](descriptors/service-descriptor.html). See [deploying a service with a descriptor](../operations/services/deploy-service.html#apply) for how to apply descriptors.

#### <a href="about:blank#_oidc_discovery"></a> OIDC discovery

```yaml
name: my-service
service:
  image: my-image:latest
  jwt:
    keySets:
    - issuer: https://accounts.google.com
      useOidcDiscovery: true
    - issuer: https://my-org.auth0.com/
      useOidcDiscovery: true
```

#### <a href="about:blank#_jwks_url"></a> JWKS URL

```yaml
name: my-service
service:
  image: my-image:latest
  jwt:
    keySets:
    - issuer: https://example.com
      jwksUrl: https://example.com/.well-known/jwks.json
      refreshInterval: 30m
```

#### <a href="about:blank#_secret"></a> Secret

```yaml
name: my-service
service:
  image: my-image:latest
  jwt:
    keySets:
    - issuer: my-issuer
      secret:
        name: my-jwks-secret
        key: jwks.json
```

#### <a href="about:blank#_multiple_keysets"></a> Multiple keysets

Multiple keysets can be configured for a single service. Each keyset can have a different issuer and source:

```yaml
name: my-service
service:
  image: my-image:latest
  jwt:
    keySets:
    - issuer: https://accounts.google.com
      useOidcDiscovery: true
    - issuer: https://example.com
      jwksUrl: https://example.com/.well-known/jwks.json
    - issuer: internal-issuer
      secret:
        name: internal-jwks
        key: jwks.json
      allowedAlgorithms:
      - RS256
```
See [Service Descriptor reference](descriptors/service-descriptor.html) for the full `JwtKeySet` field reference.

## <a href="about:blank#jwt-keys-deprecated"></a> Configuring JWT keys (deprecated)

|  | The `akka services jwts` commands and the `keys` field in service descriptors are deprecated. Use [JWKS keysets](about:blank#jwks) instead. To migrate existing keys to keysets, use `akka service jwks migrate`. |
The legacy `akka services jwts` commands configure raw JWT keys directly on a service.

Each service can have multiple keys to handle JWTs from different sources or for various destinations. Akka decides on a key to use first by *issuer*, then by *key id*. If a JWT has no issuer defined, then all keys are considered capable of signing or validating it. If a JWT has no key id defined, then the first key in the list that matches the issuer and algorithm being used is chosen.

- **Listing keys**: View existing JWT keys:

```shell
akka services jwts list <my-service>
```

|  | This command does not output the secrets themselves. To see the secrets, you can output as JSON:

```shell
akka services jwts list <my-service> -o json
``` |
- **Generating a key**: This generates a new JWT key and configures your service with it:

```shell
akka services jwts generate <my-service> \
  --key-id <my-key-id> \
  --algorithm HS256 \
  --issuer <my-issuer> \
  --secret <my-secret-name>
```

|  | This will do two things, it will:

    - Create a new secret suitable for use with the selected algorithm named according to the `--secret` argument.
    - Add a JWT key to the service that references that secret.
The `--issuer` is optional, but recommended. It will ensure that if a JWT specifies an issuer claim (`iss`), only that key will be used to verify that claim. This prevents spoofing of issuer claims. The `--key-id` is required, and should be unique to the service.

The `--secret` argument is optional, if not present, the name of the secret will be the argument passed to `--key-id`. |
- **Adding a key**: Use an existing key by adding it to the service configuration:

```shell
akka services jwts add <my-service> \
  --key-id <my-key-id> \
  --algorithm HS256 \
  --issuer <my-issuer> \
  --secret <my-secret-name>
```

### <a href="about:blank#_managing_secrets_for_jwt_keys"></a> Managing secrets for JWT keys

JWT secrets are essential for signing and validating tokens, and Akka supports both symmetric and asymmetric secret types.

- **Creating symmetric secrets**:

```shell
akka secrets create symmetric <my-secret-name> --secret-key-literal "<some-secret-text>"
```

|  | The secret can also come from a file:

```shell
akka secrets create symmetric <my-secret-name> --secret-key /path/to/secret.key
``` |
- **Creating asymmetric secrets**:

  1. **Specify a private and public key**:

```shell
akka secrets create asymmetric <my-secret-name> \
  --private-key /path/to/private.key \
  --public-key /path/to/public.key
```
  2. **Extract the public key** (optional): If only a private key is provided, Akka can automatically extract the public key:

```shell
akka secrets create asymmetric <my-secret-name> \
  --private-key /path/to/private.key \
  --extract-public-key
```

|  | Formats supported: The public and private keys must be PEM encoded keys, either RSA, ECDSA or Ed25519. We recommend PKCS8 encoded private keys (that is, keys with a PEM header of `BEGIN PRIVATE KEY`) and PKIX encoded public keys (with a header of `BEGIN PUBLIC KEY`), but we also support PKCS1 (`BEGIN RSA PRIVATE/PUBLIC KEY`) and SEC.1 (`BEGIN EC PRIVATE KEY`). |

## <a href="about:blank#_using_jwts"></a> Using JWTs

Once configured, JWTs can be used for endpoint-specific authentication and authorization.

- **Specify Required JWTs for Endpoints**: Define which endpoints require JWTs and specify validation criteria for claims. Refer to the [Authentication with JWTs in the Akka SDK](../sdk/auth-with-jwts.html) for using endpoint security.

## <a href="about:blank#_see_also"></a> See also

- [Authentication with JWTs in the Akka SDK](../sdk/auth-with-jwts.html)
- [Akka Service Descriptor](descriptors/service-descriptor.html)
- <a href="cli/akka-cli/akka_services_jwts.html#_see_also">`akka service jwts` commands</a>
- <a href="cli/akka-cli/akka_secrets.html#_see_also">`akka secrets` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Advanced views](views/concepts/advanced-views.html) [OpenID connect](security/oidc-setup.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->