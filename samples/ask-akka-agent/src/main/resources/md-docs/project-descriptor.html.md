<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Project descriptor](project-descriptor.html)

<!-- </nav> -->

# Project Descriptor reference

## <a href="about:blank#_akka_project_descriptor"></a> Akka Project descriptor

Akka’s *Project Descriptor* is a multi-document YAML file that defines the complete configuration for an Akka project, including

1. [the project itself](about:blank#project),
2. [services](about:blank#service),
3. [routes](about:blank#route),
4. [observability settings](about:blank#observability),
5. [external secrets](about:blank#external-secret),
6. [secrets](about:blank#secret),
7. [service configs](about:blank#service-config),
8. [message broker configuration](about:blank#broker), and
9. [role bindings](about:blank#rolebindings).
This allows you to manage multiple resources in a single file and apply them at once with the `akka project apply` command.

Each document in the project descriptor represents a single resource and follows a common structure:

| Field | Type | Description |
| --- | --- | --- |
| **resource** | string *required* | The type of resource: `Project`, `Service`, `Route`, `Observability`, `ExternalSecret`, `Secret`, `ServiceConfig`, `Broker`, or `RoleBindings` |
| **resourceVersion** | string *required* | The version of the resource schema. Currently only `v1` is supported. |
| **metadata** | [Metadata](about:blank#_metadata) | Optional metadata for the resource. Required for `Project`, `Service`, `Route`, `ExternalSecret`, `Secret`, and `ServiceConfig` resources. |
| **spec** | object *required* | The specification for the resource. Structure depends on the resource type. |

### <a href="about:blank#_metadata"></a> Metadata

| Field | Type | Description |
| --- | --- | --- |
| **name** | string | The name of the resource. Required for `Project`, `Service`, `Route`, `ExternalSecret`, `Secret`, and `ServiceConfig` resources. |
| **region** | string | The target region for region-specific resources. Supported for `ExternalSecret`, `Secret`, and `ServiceConfig`. When set, the resource is applied only to the specified region with regional sync mode. |

## <a href="about:blank#_resource_types"></a> Resource types

The project descriptor supports the following resource types. A single descriptor can contain multiple `Service`, `Route`, `ExternalSecret`, `Secret`, and `ServiceConfig` documents, each with a unique name. The `Project`, `Observability`, `Broker`, and `RoleBindings` resources are project-level singletons—only one of each can exist per project.

### <a href="about:blank#project"></a> Project

Defines the project itself, including its name, description, regions, and organization. If present, it must be the first document in the descriptor. When applied, the project is created if it does not exist, or updated if it does (description changes, regions added or removed).

| Field | Type | Description |
| --- | --- | --- |
| **description** | string | A description for the project. |
| **organization** | string | The organization that owns the project. Can also be specified via `--organization` flag or `akka config set organization`. |
| **regions** | []string | List of regions for the project. The first region is the primary region. Can also be specified via `--project-regions` flag. |

|  | You cannot specify both a `Project` resource in the descriptor and the `--project` flag. Use one or the other to identify the project. |

```yaml
resource: Project
resourceVersion: v1
metadata:
  name: my-project
spec:
  description: My production project
  organization: my-org
  regions:
    - us-east-1
    - eu-west-1
```

### <a href="about:blank#service"></a> Service

Defines an Akka service with its container image, environment variables, and runtime configuration. A descriptor can include multiple Service documents, each with a unique name. See [Service Descriptor reference](service-descriptor.html) for the complete reference.

```yaml
resource: Service
resourceVersion: v1
metadata:
  name: my-service
spec:
  image: acr.us-east-1.akka.io/my-org/my-project/my-service:1.0.0
  resources:
    runtime:
      mode: embedded
  env:
    - name: ENABLE_SOME_FEATURE
      value: true
```

### <a href="about:blank#route"></a> Route

Defines how ingress traffic is routed to services. A descriptor can include multiple Route documents, each with a unique name. See [Route Descriptor reference](route-descriptor.html) for the complete reference.

```yaml
resource: Route
resourceVersion: v1
metadata:
  name: api-gateway
spec:
  routes:
    - prefix: /api
      route:
        service: my-service
```

### <a href="about:blank#observability"></a> Observability

Configures the project’s observability settings for exporting metrics, logs, and traces to third-party services. Only one observability configuration exists per project. See [Observability Descriptor reference](observability-descriptor.html) for the complete reference.

```yaml
resource: Observability
resourceVersion: v1
spec:
  exporter:
    kalixConsole: {}
```

### <a href="about:blank#external-secret"></a> ExternalSecret

Defines external secrets. A descriptor can define multiple ExternalSecret documents, each with a unique name. See [External Secret Descriptor reference](external-secret-descriptor.html) for the complete reference.

```yaml
resource: ExternalSecret
resourceVersion: v1
metadata:
  name: my-secret
spec:
  azure:
    keyVaultName: my-vault
    tenantId: 09d34770-a00f-4aa1-901c-ecd362555564
    clientId: 10819e36-4be2-4b47-a7d0-8c78c94c14b6
    objects:
    - name: my-secret
      type: secret
```

### <a href="about:blank#secret"></a> Secret

Defines a secret for storing sensitive data such as credentials, certificates, or keys. A descriptor can include multiple Secret documents, each with a unique name. Secrets can be targeted to a specific region using `metadata.region`.

| Field | Type | Description |
| --- | --- | --- |
| **type** | string *required* | The secret type. Valid values: `generic`, `tls`, `tls-ca`, `symmetric`, `asymmetric`. |
| **data** | map[string]string | Key-value pairs of secret data. |

```yaml
resource: Secret
resourceVersion: v1
metadata:
  name: my-credentials
spec:
  type: generic
  data:
    username: my-user
    password: my-password
```
Values are strings, so structured data such as JSON can be embedded using the YAML block scalar syntax:

```yaml
resource: Secret
resourceVersion: v1
metadata:
  name: gcp-service-account
spec:
  type: generic
  data:
    credentials.json: |
      {
        "type": "service_account",
        "project_id": "my-gcp-project",
        "private_key_id": "key-id",
        "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
        "client_email": "sa@my-gcp-project.iam.gserviceaccount.com"
      }
```
To target a specific region:

```yaml
resource: Secret
resourceVersion: v1
metadata:
  name: my-regional-secret
  region: us-east-1
spec:
  type: generic
  data:
    api-key: region-specific-key
```

### <a href="about:blank#service-config"></a> ServiceConfig

Defines a service configuration containing HOCON configuration text that can be referenced by services. A descriptor can include multiple ServiceConfig documents, each with a unique name. Service configs can be targeted to a specific region using `metadata.region`.

| Field | Type | Description |
| --- | --- | --- |
| **config** | string *required* | The HOCON configuration text. |

```yaml
resource: ServiceConfig
resourceVersion: v1
metadata:
  name: my-config
spec:
  config: |
    some.setting = "value"
    another.setting = 42
```

### <a href="about:blank#broker"></a> Broker

Configures the message broker for the project. Only one broker configuration is supported per project.

| Field | Type | Description |
| --- | --- | --- |
| **type** | string *required* | The broker type: `google-pubsub`, `kafka`, or `eventhubs` |
| **description** | string | Optional description of the broker configuration |
| **gcpKey** | string | Required for `google-pubsub`. The name of a secret containing the GCP service account key. |
| **kafka** | [KafkaSASLSpec](about:blank#KafkaSASLSpec) | Required for `kafka`. Kafka SASL authentication configuration. |
| **eventhubs** | [EventHubsSpec](about:blank#EventHubsSpec) | Required for `eventhubs`. Azure Event Hubs configuration. |

#### <a href="about:blank#KafkaSASLSpec"></a> KafkaSASLSpec

Configuration for Kafka brokers using SASL authentication.

| Field | Type | Description |
| --- | --- | --- |
| **authMechanism** | string *required* | The SASL authentication mechanism. Supported values: `PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-512`. |
| **servers** | string *required* | Comma-separated list of Kafka bootstrap servers, e.g. `broker1:9092,broker2:9092`. |
| **username** | string *required* | The username for SASL authentication. |
| **passwordSecret** | string *required* | Reference to the secret containing the password, in the format `secret-name/key`. |
| **caCertSecret** | string | Optional reference to a secret containing the CA certificate for TLS, in the format `secret-name/key`. |
Example:

```yaml
resource: Broker
resourceVersion: v1
spec:
  type: kafka
  description: Production Kafka cluster
  kafka:
    authMechanism: SCRAM-SHA-256
    servers: kafka-1.example.com:9092,kafka-2.example.com:9092
    username: akka-service

    passwordSecret: kafka-credentials/password
    caCertSecret: kafka-credentials/ca-cert
```

#### <a href="about:blank#EventHubsSpec"></a> EventHubsSpec

Configuration for Azure Event Hubs.

| Field | Type | Description |
| --- | --- | --- |
| **connectionStringSecret** | string *required* | Reference to the secret containing the Event Hubs connection string, in the format `secret-name/key`. |
| **checkpointStoreSasTokenSecret** | string *required* | Reference to the secret containing the SAS token for the checkpoint store, in the format `secret-name/key`. |
| **checkpointStoreEndpoint** | string *required* | The Azure Blob Storage endpoint for checkpointing, e.g. `https://mystorageaccount.blob.core.windows.net`. |
| **checkpointStoreContainerName** | string *required* | The name of the blob container used for checkpointing. |
Example:

```yaml
resource: Broker
resourceVersion: v1
spec:
  type: eventhubs
  description: Azure Event Hubs for production
  eventhubs:
    connectionStringSecret: eventhubs-credentials/connection-string
    checkpointStoreSasTokenSecret: eventhubs-credentials/sas-token
    checkpointStoreEndpoint: https://mystorageaccount.blob.core.windows.net
    checkpointStoreContainerName: checkpoints
```

#### <a href="about:blank#_google_pubsub"></a> Google Pub/Sub

For Google Pub/Sub, provide the name of a secret containing the GCP service account key:

```yaml
resource: Broker
resourceVersion: v1
spec:
  type: google-pubsub
  description: Google Pub/Sub for production
  gcpKey: gcp-service-account
```

### <a href="about:blank#rolebindings"></a> RoleBindings

Configures the project’s OpenID Connect (OIDC) group-to-role mappings. Only one role binding configuration exists per project, but it can contain multiple group-to-role mappings. This enables automated role assignment based on group membership when using OIDC authentication at the organization level. The project must be owned by an organization with an OIDC setup.

| Field | Type | Description |
| --- | --- | --- |
| **bindings** | [] [GroupRoleBinding](about:blank#GroupRoleBinding) | List of group-to-role mappings. |

#### <a href="about:blank#GroupRoleBinding"></a> GroupRoleBinding

| Field | Type | Description |
| --- | --- | --- |
| **group** | string *required* | The OIDC group name from your identity provider. Must be a simple group name, not a full resource path. |
| **role** | string *required* | The Akka project role to assign. Valid values: `admin`, `developer`, `viewer`, `backoffice`. |
Example:

```yaml
resource: RoleBindings
resourceVersion: v1
spec:
  bindings:
    - group: platform-admins
      role: admin
    - group: developers
      role: developer
    - group: support-team
      role: viewer
```

## <a href="about:blank#_using_the_project_descriptor"></a> Using the project descriptor

### <a href="about:blank#exporting"></a> Exporting the descriptor for an existing project

To export all resources from an existing project as a descriptor:

```bash
akka project export --project my-project --file my-project.yaml
```
This exports the project configuration, services, routes, secrets, service configs, observability configuration, broker settings, and role bindings to a single file. You can also write to standard output:

```bash
akka project export > project.yaml
```
The exported descriptor can be used as a starting point for version-controlled infrastructure, or to replicate a project’s configuration.

|  | Secret values are redacted in the exported descriptor. Secret data is replaced with `NOT EXPORTED` placeholders to prevent accidental exposure. You must replace these placeholders with actual values before re-applying the descriptor. |

### <a href="about:blank#applying"></a> Applying a project descriptor

To configure the project according to the descriptor, use the `akka project apply` command.

#### <a href="about:blank#_dry_run_validation"></a> Dry-run validation

The `--dry-run` flag validates a descriptor and shows what would be applied without making any changes:

```bash
akka project apply --file project.yaml --dry-run
```
Dry-run performs the following validations:

- Schema validation for all resource types
- Cross-resource reference checking — for example, if a Service references `serviceConfig: my-config`, dry-run verifies that `my-config` exists either as a `ServiceConfig` document in the same descriptor or as an existing resource in the project
- Secret references are validated the same way
- Region names are validated against the project’s available regions

#### <a href="about:blank#_applying"></a> Applying

To apply a project descriptor:

```bash
akka project apply --file project.yaml
```
This command will create or update all resources defined in the descriptor. Resources are processed in order, and the command will report which resources were created, updated, or skipped.

|  | The apply operation is additive. It creates or updates resources defined in the descriptor, but does not remove resources that are absent from the file. If you previously applied a descriptor with three services and then apply a new version with only two services, the third service will remain deployed. To remove a resource, use the appropriate `akka` CLI delete command (e.g., `akka service delete`). |

|  | For multi-region projects, the descriptor is applied to the primary region. Changes will then be synchronized to other regions automatically. |

#### <a href="about:blank#_reading_from_standard_input"></a> Reading from standard input

You can also pipe the descriptor content:

```bash
cat project.yaml | akka project apply --file -
```
This is particularly useful in CI/CD pipelines where secret values are injected from environment variables. Keep the descriptor in source control as a template with placeholders, and substitute values at apply time:

```yaml
resource: Secret
resourceVersion: v1
metadata:
  name: my-credentials
spec:
  type: generic
  data:
    password: ${DB_PASSWORD}
```

```bash
envsubst < project.yaml | akka project apply --file -
```
This way the descriptor never contains actual secret values — they are resolved from the CI environment at apply time.

## <a href="about:blank#example"></a> Complete descriptor example

Here is a complete project descriptor demonstrating multiple resource types:

```yaml
resource: Project
resourceVersion: v1
metadata:
  name: my-project
spec:
  description: E-commerce application
  organization: my-org
  regions:
    - us-east-1
---
resource: Service
resourceVersion: v1
metadata:
  name: cart-service
spec:
  image: acr.us-east-1.akka.io/my-org/my-project/cart-service:1.0.0
  serviceConfig: cart-config
  resources:
    runtime:
      mode: embedded
  env:
    - name: LOG_LEVEL
      value: INFO
---
resource: Service
resourceVersion: v1
metadata:
  name: product-catalog
spec:
  image: acr.us-east-1.akka.io/my-org/my-project/product-catalog:2.1.0
  resources:
    runtime:
      mode: embedded
  labels:
    team: commerce
    tier: backend
---
resource: Route
resourceVersion: v1
metadata:
  name: api-gateway
spec:
  routes:
    - prefix: /carts
      route:
        service: cart-service
    - prefix: /products
      route:
        service: product-catalog
---
resource: ServiceConfig
resourceVersion: v1
metadata:
  name: cart-config
spec:
  config: |
    cart.max-items = 100
    cart.expiry = 24h
---
resource: Secret
resourceVersion: v1
metadata:
  name: kafka-credentials
spec:
  type: generic
  data:
    password: my-kafka-password
---
resource: Observability
resourceVersion: v1
spec:
  exporter:
    kalixConsole: {}
---
resource: Broker
resourceVersion: v1
spec:
  type: kafka
  description: Production Kafka cluster
  kafka:
    authMechanism: SCRAM-SHA-256
    servers: kafka.example.com:9092
    username: akka-service
    passwordSecret: kafka-credentials/password
---
resource: RoleBindings
resourceVersion: v1
spec:
  bindings:
    - group: platform-admins
      role: admin
    - group: developers
      role: developer
```

## <a href="about:blank#_related_documentation"></a> Related documentation

- [Service Descriptor reference](service-descriptor.html)
- [Route Descriptor reference](route-descriptor.html)
- [Observability Descriptor reference](observability-descriptor.html)
- <a href="../cli/akka-cli/akka_projects.html">`akka projects` CLI commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Reference](../index.html) [Service descriptor](service-descriptor.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->