# JSON Web Tokens (JWTs)

JWTs allow authentication and authorization of requests, especially for internet-facing applications and inter-service communication. JWTs provide a trusted mechanism for validating identities and permissions, ensuring secure interactions between services, users, and devices.

This section presents how you create and manage JWTs with Akka. Using JWTs to secure services is presented in [Authentication with JWTs in the Akka SDK](java:auth-with-jwts.adoc).

## Key Concepts of JWTs
* **Claims**: JWTs consist of claims and a signature. Claims represent user identity, roles, permissions, or access rights, allowing services to interpret what actions the JWT holder can perform.
* **Signature**: Ensures the integrity of the JWT, verifying that it was issued by a trusted entity and has not been tampered with.

### Use Cases
* **User and Device Authentication**: JWTs authenticate internet users or devices and can define what actions they’re authorized to perform.
* **Inter-Service Authorization**: JWTs facilitate secure serverless operations, where services validate tokens directly without needing an external verifier.

**💡 TIP**\
Another common use case is to use claims for authorization. Imagine a social network, one of the services in that network is a friends service. When the user fetches their list of friends from the friends service, it may send them a JWT for each friend in their friends list. For each friend, the corresponding JWT will contain a claim indicating that the user is a friend of the logged in user. When the logged in user wants to send a message to another friend, they can include the JWT of that friend in their request to send a message, and the message service can verify that the JWT has the necessary friendship claim, and allow the message to be sent.

## JWT Signing Approaches
JWTs can be signed using _symmetric_ or _asymmetric_ keys:

* **Symmetric Keys**: Simple setup where both issuer and verifier share a secret key. Useful in trusted environments.
* **Asymmetric Keys**: Use a private key to sign and a public key to verify. Useful when verifying parties are untrusted, as only the private key can sign tokens.

## Configuring JWT keys

Before the JWT feature can be used, JWT keys need to be set up for a service.

### Supported algorithms
Akka supports a range of algorithms, including HMAC (HS256, HS384, HS512) for symmetric keys and RSA/ECDSA/Ed25519 for asymmetric keys.

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
| Ed25519 | Ed25519 | Asymmetric |

<dl><dt><strong>📌 NOTE: Recommendation:</strong></dt><dd>

Use HS256 for symmetric keys and ES256 for asymmetric keys.
</dd></dl>

### Setting up JWT keys

Each service can have multiple keys to handle JWTs from different sources or for various destinations. Akka decides on a key to use first by _issuer_, then by _key id_. If a JWT has no issuer defined, then all keys are considered capable of signing or validating it. If a JWT has no key id defined, then the first key in the list that matches the issuer and algorithm being used is chosen.

* **Listing keys**: View existing JWT keys:

  ```shell
  akka services jwts list <my-service>
  ```

  This command does not output the secrets themselves. To see the secrets, you can output as JSON:

  ```shell
  akka services jwts list <my-service> -o json
  ```
* **Generating a key**: This generates a new JWT key and configures your service with it:

  ```shell
  akka services jwts generate <my-service> \
    --key-id <my-key-id> \
    --algorithm HS256 \
    --issuer <my-issuer> \
    --secret <my-secret-name>
  ```

  This will do two things, it will:

  * Create a new secret suitable for use with the selected algorithm named according to the `--secret` argument.
  * Add a JWT key to the service that references that secret.

  The `--issuer` is optional, but recommended. It will ensure that if a JWT specifies an issuer claim (`iss`), only that key will be used to verify that claim. This prevents spoofing of issuer claims. The `--key-id` is required, and should be unique to the service.

  The `--secret` argument is optional, if not present, the name of the secret will be the argument passed to `--key-id`.
* **Adding a key**: Use an existing key by adding it to the service configuration:

  ```shell
  akka services jwts add <my-service> \
    --key-id <my-key-id> \
    --algorithm HS256 \
    --issuer <my-issuer> \
    --secret <my-secret-name>
  ```

## Managing secrets

JWT secrets are essential for signing and validating tokens, and Akka supports both symmetric and asymmetric secret types.

* **Creating symmetric secrets**:

  ```shell
  akka secrets create symmetric <my-secret-name> --secret-key-literal "<some-secret-text>"
  ```

  The secret can also come from a file:

  ```shell
  akka secrets create symmetric <my-secret-name> --secret-key /path/to/secret.key
  ```
* **Creating asymmetric secrets**:

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

**Formats supported:**

The public and private keys must be PEM encoded keys, either RSA, ECDSA or Ed25519. We recommend PKCS8 encoded private keys (that is, keys with a PEM header of `BEGIN PRIVATE KEY`) and PKIX encoded public keys (with a header of `BEGIN PUBLIC KEY`), but we also support PKCS1 (`BEGIN RSA PRIVATE/PUBLIC KEY`) and SEC.1 (`BEGIN EC PRIVATE KEY`).

## Using JWTs

Once configured, JWTs can be used for endpoint-specific authentication and authorization.

* **Specify Required JWTs for Endpoints**: Define which endpoints require JWTs and specify validation criteria for claims. Refer to the [Authentication with JWTs in the Akka SDK](java:auth-with-jwts.adoc) for using endpoint security.

## See also
* [Authentication with JWTs in the Akka SDK](java:auth-with-jwts.adoc)
* [`akka service jwt` commands](reference:cli/akka-cli/akka_services_jwts.adoc#_see_also)
* [`akka secrets` commands](reference:cli/akka-cli/akka_secrets.adoc#_see_also)
