<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Service descriptor](service-descriptor.html)

<!-- </nav> -->

# Service Descriptor reference

## <a href="about:blank#_akka_service_descriptor"></a> Akka Service descriptor

A *Service Descriptor* in Akka defines the configuration for a service resource. This is used by the `akka service apply command` for deploying a service together with a detailed configuration. Below is the detailed reference for each part of the service descriptor and its fields.

The Service Descriptor is used by the `akka service apply` command, described in [deploying a service with a descriptor](../../operations/services/deploy-service.html#apply).

| Field | Type | Description |
| --- | --- | --- |
| **name** | string *required* | The name of the service |
| **service** | [ServiceSpec](about:blank#_servicespec) *required* | Specification for the Akka service |

### <a href="about:blank#_servicespec"></a> ServiceSpec

The **ServiceSpec** defines the detailed configuration of an Akka service, including container images, environment variables, and resource management.

| Field | Type | Description |
| --- | --- | --- |
| **image** | string *required* | The name of the docker image for the service container |
| **env** | [] [EnvVar (Environment Variables)](about:blank#EnvVar) | Environment variables to be set in the service’s container |
| **jwt** | [ServiceJwt](about:blank#_servicejwt) | JWT configuration for the service |
| **resources** | [ServiceResources](about:blank#_serviceresources) | Resource configuration for the service, including instance size and autoscaling |
| **volumeMounts** | [] [VolumeMount](about:blank#_volumemount) | Volume mounts to mount in the service’s container |
| **telemetry** | [] [ServiceTelemetry](about:blank#_servicetelemetry) | Telemetry configuration for the service |
| **replication** | [ServiceReplication](about:blank#_servicereplication) | Replication configuration for stateful components in the service |
| **tls** | [TLSConfig](about:blank#TLSConfig) | TLS configuration for outbound connections from the service |

### <a href="about:blank#EnvVar"></a> EnvVar (Environment Variables)

| Field | Type | Description |
| --- | --- | --- |
| **name** | string *required* | The name of the environment variable |
| **value** | string | The value of the environment variable. Either this must be set, or **valueFrom** must be set, but not both. |
| **valueFrom** | [EnvVarSource (referencing secrets from Environment Variables)](about:blank#EnvVarSource) | Configuration for where to get the value of the environment variable from. Either this must be set, or **value** must be set, but not both. |

### <a href="about:blank#EnvVarSource"></a> EnvVarSource (referencing secrets from Environment Variables)

The source for an environment variable.

| Field | Type | Description |
| --- | --- | --- |
| **secretKeyRef** | [SecretKeyRef (details of a referenced secret)](about:blank#SecretKeyRef) *required* | A reference to a secret. |

### <a href="about:blank#SecretKeyRef"></a> SecretKeyRef (details of a referenced secret)

A reference to a particular key in a particular secret, used by environment variables.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string *required* | The name of a configured secret |
| **key** | string *required* | The name of a key in the specified secret |
| **optional** | boolean | Whether the secret or its key must exist. If true, the service will fail to deploy if the secret or its key doesn’t exist. If false, the service will be deployed with the environment variable unset if the secret doesn’t or its key doesn’t exist. |

### <a href="about:blank#_servicejwt"></a> ServiceJwt

The JWT configuration allows a service to manage token signing and validation. Keys are prioritized for matching tokens.

| Field | Type | Description |
| --- | --- | --- |
| **keys** | [] [JwtKey](about:blank#_jwtkey) | List of JWT keys for signing and validation. Order matters. |
The order of **keys** is important. When signing or validating, the first matching key will be used, according to the following rules.

For validating:

- The list is first filtered by all keys capable of validating - that is, any keys for the HMAC algorithm, or any keys for an asymmetric algorithm that have the public key defined.
- If the token being validated defines an issuer (iss), and there is at least one key that has that issuer defined, then the list is filtered to only include keys from that issuer. Otherwise, all keys capable of validating remain as candidates.
- The list is then filtered matching the algorithm. The algorithm only has to match by key type, that is, by HMAC, RSA, ECDSA or EdDSA. A key that has an algorithm of HS256 can be used to validate a token signed by an algorithm of HS512.
- If at this point, there are still multiple keys remaining, then the first key that matches the key id (kid) parameter, if defined, in the token is used, otherwise, the first key of the remaining keys is used.
For signing:

- The list is first filtered by all keys capable of signing - that is, any keys for the HMAC algorithm, or any keys for an asymmetric algorithm that have the private key defined.
- If the token to be signed contains an issuer claim (iss), and there is at least one key that has that same issuer defined, then the list is filtered to only include the keys with that issuer, otherwise, all keys capable of signing remain as candidates. The first key out of the remaining keys is used.

### <a href="about:blank#_jwtkey"></a> JwtKey

A key that can be used for Akka’s JWT support.

| Field | Type | Description |
| --- | --- | --- |
| **keyId** | string *required* | The id of the key.

When signing, it will be placed in the key id parameter (kid) of the JWT header. It may be used for matching incoming keys when validating. It is important that, for a given issuer, if two keys have the same id, that those keys must be the same key.

Ideally, key ids should be unique across all services.

The key id allows JWT keys to be rotated: a new key can be configured with a lower priority on all services that use it. Once that change is pushed out, then the key’s priority, i.e., the order in the list, can be increased so that it is now the key that gets used for signing. Finally, once all JWTs signed by the old key have expired, the old key can be removed. |
| **issuer** | string | The issuer of tokens that use this key.

If a token being signed or validated contains an issuer (iss) claim, this will be matched against it. This allows multiple keys for different issuers to be provisioned without conflicting.

Setting this parameter is also important if you have keys from multiple issuers, but you don’t trust that one of them won’t try and spoof the other. Since it pins a particular set of keys to only be used to validate that issuer’s token, if the other tries to spoof it, validation will fail. |
| **algorithm** | string *required* | The algorithm to use this key for.

When validating, this secret may be used if the secret for this algorithm is compatible with the algorithm that the token being validated was signed with.

Valid values are:

  - `HMD5` - HMAC with MD5
  - `HS224` - HMAC with SHA224
  - `HS256` - HMAC with SHA256
  - `HS384` - HMAC with SHA384
  - `HS512` - HMAC with SHA512
  - `RS256` - RSA with SHA256
  - `RS384` - RSA with SHA384
  - `RS512` - RSA with SHA512
  - `ES256` - Elliptic Curve DSA with SHA256
  - `ES384` - Elliptic Curve DSA with SHA384
  - `ES512` - Elliptic Curve DSA with SHA512
  - `Ed25519` - Edward’s Curve DSA |
| **secret** | [ObjectRef](about:blank#_objectref) *required* | The configured secret to use for signing or validating.

This must be a reference to a secret. The secret must have the following keys defined, depending on the algorithm used:

  - HMAC algorithms - a key named `secret.key`, containing the bytes of the secret.
  - Asymmetric algorithms - One or both of `private.key` and `public.key`. If only a `private.key` is supplied, the key will only be used for signing, if only a public key is supplied, the key will only be used for validating. The public key must be formatted as an X.509 PEM public key (with a header, `BEGIN PUBLIC KEY`). The private key must be a PEM PKCS-8 key encoded (with a header, `BEGIN PRIVATE KEY`) according to the algorithm the secret is used for. Alternatively, for RSA algorithms, RSA private keys may be PEM PKCS-1 encoded (with a header, `BEGIN RSA PRIVATE KEY`). The keys must not be encrypted. For ECDSA keys, EC key encoding (`BEGIN EC PRIVATE KEY`) is not supported, and PKCS-8 must be used instead. |

### <a href="about:blank#_objectref"></a> ObjectRef

A reference to an object, such as a configured secret.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string *required* | The name of the object. |

### <a href="about:blank#_serviceresources"></a> ServiceResources

The configuration for the resources used by an Akka Service.

| Field | Type | Description |
| --- | --- | --- |
| **instanceType** | string | The type of instance to use. Supported values are:

  - `small` - A small instance. |
| **autoscaling** | [ServiceAutoscaling](about:blank#_serviceautoscaling) | The autoscaling configuration for a service. Autoscaling is only supported for non-trial projects. |
| **runtime** | [Runtime](about:blank#_runtime) | The runtime configuration for the service. |

### <a href="about:blank#_runtime"></a> Runtime

The runtime configuration for the service.

| Field | Type | Description |
| **mode** | string | The runtime mode. Supported values are: `sidecar` or `embedded`. If the field is not defined the default mode of the service is the `sidecar` mode. |

- `sidecar` -  runs the service with the `Kalix` runtime.
- `embedded` - runs the service with the `Akka` runtime

### <a href="about:blank#_serviceautoscaling"></a> ServiceAutoscaling

The configuration for how the Akka service is scaled in response to load.

| Field | Type | Description |
| --- | --- | --- |
| **minInstances** | int | The minimum instances of a service that should be available. Must be at least 1, and no greater than **maxInstances**. May not be greater than 10. |
| **maxInstances** | int | The maximum instances of a service that should be available. Must be at least 1, and no less than **minInstances**. May not be greater than 10. |
| **cpuUsageThreshold** | int | The target CPU usage for autoscaling to achieve. Once CPU usage across all instances exceeds this, the service will be scaled up. Must be at least 1 and no greater than 100. |

### <a href="about:blank#_volumemount"></a> VolumeMount

A volume that should be mounted into the service container. Only one source may be specified.

| Field | Type | Description |
| --- | --- | --- |
| **mountPath** *required* | string | Path within the container at which the volume should be mounted. Must not contain ':'. |
| **secret** | [SecretVolumeSource](about:blank#_secretvolumesource) | A secret that should be used to populate this volume. |
| **externalSecret** | [ExternalSecretVolumeSource](about:blank#_externalsecretvolumesource) | An external secret that should be used to populate this volume. |

### <a href="about:blank#_secretvolumesource"></a> SecretVolumeSource

Adapts a secret into a volume that can be mounted into the service’s container.

| Field | Type | Description |
| --- | --- | --- |
| **secretName** *required* | string | The name of a secret in the service’s project to mount. |
| **defaultMode** | int | Mode bits to set the permissions on created files from the secret by default. Must be an octal value between 0000 and 0777, or a decimal value between 0 and 511. Defaults to 0644. |
| **optional** | boolean | Specifies whether the container should fail to start if the secret doesn’t exist. |
Example of using a volume mount to mount a secret named `google-application-credentials` at path `/google-application-credentials-path`, which is later referenced by an environment variable named `GOOGLE_APPLICATION_CREDENTIALS` pointing to a file named `credentials.json` in that path:

```bash
akka secret create generic google-application-credentials --from-file credentials.json=/path/to/credentials.json
```

```yaml
name: <<service-name>>
service:
  volumeMounts:
  - mountPath: /google-application-credentials-path
    secret:
      secretName: google-application-credentials
  env:
  - name: GOOGLE_APPLICATION_CREDENTIALS
    value: /google-application-credentials-path/credentials.json
```

### <a href="about:blank#_externalsecretvolumesource"></a> ExternalSecretVolumeSource

Adapts an external secret into a volume that can be mounted into the service’s container.

| Field | Type | Description |
| --- | --- | --- |
| **provider** *required* | string | The name of the external secret to mount. |

### <a href="about:blank#_servicetelemetry"></a> ServiceTelemetry

Telemetry configuration for the service.

| Field | Type | Description |
| --- | --- | --- |
| **tracing** | [] [ServiceTracing](about:blank#_servicetracing) | Tracing configuration for the service |

### <a href="about:blank#_servicetracing"></a> ServiceTracing

Tracing configuration for the service.

| Field | Type | Description |
| --- | --- | --- |
| **enabled** | boolean | Specifies if the service produces traces or not |

### <a href="about:blank#_servicereplication"></a> ServiceReplication

Replication configuration for stateful components, such as Entities and Workflows, in the service. See [Selecting primary for stateful components](../../operations/regions/index.html#selecting-primary) for more information and considerations before changing.

| Field | Type | Description |
| --- | --- | --- |
| **mode** | string | Replication mode. The only supported mode is `replicated-read`. Defaults to `replicated-read`. |
| **replicatedRead** | [ReplicatedRead](about:blank#_replicatedread) | Configuration of `replicated-read` mode. |

### <a href="about:blank#_replicatedread"></a> ReplicatedRead

Replicated read means that writes are handled by one primary replica and reads can be served by any replica.

| Field | Type | Description |
| --- | --- | --- |
| **PrimarySelectionMode** | string | Selection of primary can be `pinned-region`, `request-region` and `none`.

  - `pinned-region` - one region is defined as the primary for the project, and all stateful component instances will use that region as primary
  - `request-region` - the primary is selected by each individual component instance based on where the write request occurs
  - `none` - read-only mode for all regions, which causes all write requests to be rejected |

### <a href="about:blank#TLSConfig"></a> TLSConfig

TLS configuration for outbound connections from the service. This configuration affects how the service connects to external systems over TLS.

| Field | Type | Description |
| --- | --- | --- |
| **truststore** | [TruststoreConfig](about:blank#TruststoreConfig) | Truststore configuration for custom CA certificates. When configured, the truststore is merged with the JVM’s default cacerts for outbound TLS connections, ensuring both custom and public CAs are trusted. |

### <a href="about:blank#TruststoreConfig"></a> TruststoreConfig

Defines a custom JVM truststore for validating server certificates on outbound TLS connections. This is typically used when connecting to services that use certificates signed by a private/internal Certificate Authority.

Either **secret** or **externalSecret** must be specified, but not both.

| Field | Type | Description |
| --- | --- | --- |
| **secret** | [ObjectRef](about:blank#_objectref) | A reference to a secret containing the truststore file. The secret must exist in the same project as the service. |
| **externalSecret** | [ObjectRef](about:blank#_objectref) | A reference to an ExternalSecret resource that provides the truststore file from an external secret provider (e.g., Azure Key Vault). The ExternalSecret must exist in the same project as the service. |
| **key** | string | The key/filename containing the truststore file. For Secret: the key in the secret’s data field. For ExternalSecret: the filename as configured in the ExternalSecret’s objects. Defaults to `truststore`. |
| **type** | string | The truststore format. Valid values are `JKS` (Java KeyStore) and `PKCS12`. Defaults to `JKS`. |
| **passwordKey** | string | The key in the secret containing the truststore password. For ExternalSecret, this refers to the filename containing the password. If not specified, no password will be used when reading the truststore. |
Example of configuring a truststore to trust a private CA for connecting to an internal service:

```bash
akka secret create generic my-truststore --from-file truststore.jks=/path/to/truststore.jks --from-literal truststore-password=changeit
```

```yaml
name: <<service-name>>
service:
  image: my-image:latest
  tls:
    truststore:
      secret:
        name: my-truststore
      key: truststore.jks
      type: JKS
      passwordKey: truststore-password
```

<!-- <footer> -->
<!-- <nav> -->
[Project descriptor](project-descriptor.html) [Route descriptor](route-descriptor.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->