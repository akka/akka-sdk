<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Descriptors reference](index.html)
- [Observability descriptor](observability-descriptor.html)

<!-- </nav> -->

# Observability Descriptor reference

## <a href="about:blank#observability"></a> Akka Observability descriptor

An Akka observability descriptor describes how metrics, logs, and traces are exported to third party services. It is used by the `akka project observability apply` command. Exporters can be optionally defined as default exporter, meaning it will be used for  the metrics, logs, and traces, but can then be optionally overridden for each of metrics, logs, and traces.

| Field | Type | Description |
| --- | --- | --- |
| **exporters** | [] [ObservabilityDefault](about:blank#_observabilitydefault) | The default exporters used for metrics, logs, and traces. Will be used for each unless a respective exporter in `logs` or `metrics` is defined. |
| **metrics** | [] [ObservabilityMetrics](about:blank#_observabilitymetrics) | The exporters to use for metrics. Overrides the exporters defined in `exporters`, but just for metrics. |
| **logs** | [] [ObservabilityLogs](about:blank#_observabilitylogs) | The exporters to use for logs. Overrides the exporters defined in `exporters`, but just for logs. |
| **traces** | [] [ObservabilityTraces](about:blank#_observabilitytraces) | The exporters to use for traces. Overrides the exporters defined in `exporters`, but just for traces. |
| **heapDump** | [HeapDump](about:blank#_heapdump) | Heap dump export configuration. |
| **traceSampling** | [TraceSampling](about:blank#_tracesampling) | Trace sampling configuration. Applies to all trace exporters. |
| **logLevel** | string | Log level for the OpenTelemetry collector. Valid values are `debug`, `info`, `warn`, and `error`. |

### <a href="about:blank#_observabilitydefault"></a> ObservabilityDefault

The default exporter configuration for metrics, logs, and traces. At most one default exporter may be configured.

| Field | Type | Description |
| --- | --- | --- |
| **kalixConsole** | object | If defined, metrics will be exported to the Akka Console. There are no configuration parameters for the console exporter, it should be declared as an empty object. |
| **otlp** | [ObservabilityOtlp](about:blank#_observabilityotlp) | If defined, will export metrics, logs, and traces to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **otlpHttp** | [ObservabilityOtlpHttp](about:blank#_observabilityotlphttp) | If defined, will export metrics, logs, and traces to an OpenTelemetry collector using the OTLP HTTP protocol. |
| **splunkHec** | [ObservabilitySplunkHec](about:blank#_observabilitysplunkhec) | If defined, will export metrics and logs to a Splunk platform instance, using the Splunk HTTP Event Collector. |
| **googleCloud** | [ObservabilityGoogleCloud](about:blank#_observabilitygooglecloud) | If defined, will export metrics, logs, and traces to Google Cloud. |
| **azureMonitor** | [ObservabilityAzureMonitor](about:blank#_observabilityazuremonitor) | If defined, will export metrics, logs, and traces to Azure Monitor. |

### <a href="about:blank#_observabilitymetrics"></a> ObservabilityMetrics

The metrics exporter configuration. At most one metrics exporter may be configured. If a default exporter is configured, the exporter configured here will override that exporter for metrics.

| Field | Type | Description |
| --- | --- | --- |
| **kalixConsole** | object | If defined, metrics will be exported to the Akka Console. There are no configuration parameters for the console exporter, it should be declared as an empty object. |
| **otlp** | [ObservabilityOtlp](about:blank#_observabilityotlp) | If defined, will export metrics to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **otlpHttp** | [ObservabilityOtlpHttp](about:blank#_observabilityotlphttp) | If defined, will export metrics to an OpenTelemetry collector using the OTLP HTTP protocol. |
| **prometheuswrite** | [ObservabilityPrometheusWrite](about:blank#_observabilityprometheuswrite) | If defined, will export metrics using the Prometheus remote write protocol. |
| **splunkHec** | [ObservabilitySplunkHec](about:blank#_observabilitysplunkhec) | If defined, will export metrics to a Splunk platform instance, using the Splunk HTTP Event Collector. |
| **googleCloud** | [ObservabilityGoogleCloud](about:blank#_observabilitygooglecloud) | If defined, will export metrics to Google Cloud. |
| **azureMonitor** | [ObservabilityAzureMonitor](about:blank#_observabilityazuremonitor) | If defined, will export metrics to Azure Monitor. |
See the [metrics reference](../telemetry/metrics.html) for a list of the available metrics that will be exported.

### <a href="about:blank#_observabilitytraces"></a> ObservabilityTraces

The traces exporter configuration. At most one traces exporter may be configured. If a default exporter is configured, the exporter configured here will override the default exporter for traces.

| Field | Type | Description |
| --- | --- | --- |
| **kalixConsole** | object | If defined, traces will be exported to the Akka Console. There are no configuration parameters for the console exporter, it should be declared as an empty object. |
| **otlp** | [ObservabilityOtlp](about:blank#_observabilityotlp) | If defined, will export traces to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **otlpHttp** | [ObservabilityOtlpHttp](about:blank#_observabilityotlphttp) | If defined, will export traces to an OpenTelemetry collector using the OTLP HTTP protocol. |
| **googleCloud** | [ObservabilityGoogleCloud](about:blank#_observabilitygooglecloud) | If defined, will export traces to Google Cloud. |
| **azureMonitor** | [ObservabilityAzureMonitor](about:blank#_observabilityazuremonitor) | If defined, will export traces to Azure Monitor. |

### <a href="about:blank#_observabilitylogs"></a> ObservabilityLogs

The logs exporter configuration. At most one logs exporter may be configured. If a default exporter is configured, the exporter configured here will override that exporter for logs.

| Field | Type | Description |
| --- | --- | --- |
| **otlp** | [ObservabilityOtlp](about:blank#_observabilityotlp) | If defined, will export logs to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **otlpHttp** | [ObservabilityOtlpHttp](about:blank#_observabilityotlphttp) | If defined, will export logs to an OpenTelemetry collector using the OTLP HTTP protocol. |
| **splunkHec** | [ObservabilitySplunkHec](about:blank#_observabilitysplunkhec) | If defined, will export logs to a Splunk platform instance, using the Splunk HTTP Event Collector. |
| **googleCloud** | [ObservabilityGoogleCloud](about:blank#_observabilitygooglecloud) | If defined, will export logs to Google Cloud. |
| **azureMonitor** | [ObservabilityAzureMonitor](about:blank#_observabilityazuremonitor) | If defined, will export logs to Azure Monitor. |

### <a href="about:blank#_observabilityotlp"></a> ObservabilityOtlp

Configuration for an OpenTelemetry exporter using the OTLP gRPC protocol.

| Field | Type | Description |
| --- | --- | --- |
| **endpoint** | string *required* | The endpoint to export OTLP metrics, logs, or traces to, for example, `my.otlp.host:443`. |
| **tls** | [ObservabilityTls](about:blank#_observabilitytls) | TLS configuration for connections to the OpenTelemetry collector. |
| **headers** | [] [ObservabilityHeader](about:blank#_observabilityheader) | A list of headers to add to outgoing requests. |

### <a href="about:blank#_observabilityprometheuswrite"></a> ObservabilityPrometheusWrite

Configuration for a Prometheus exporter using the Prometheus remote write protocol.

| Field | Type | Description |
| --- | --- | --- |
| **endpoint** | string *required* | The URL to export Prometheus remote write metrics to, for example, `https://my.cortex.host/api/v1/push`. |
| **tls** | [ObservabilityTls](about:blank#_observabilitytls) | TLS configuration for connections to the Prometheus remote write endpoint. |
| **headers** | [] [ObservabilityHeader](about:blank#_observabilityheader) | A list of headers to add to outgoing requests. |

### <a href="about:blank#_observabilitysplunkhec"></a> ObservabilitySplunkHec

Configuration for a Splunk HEC exporter to export to Splunk Platform instance using the Splunk HTTP Event Collector.

| Field | Type | Description |
| --- | --- | --- |
| **endpoint** | string *required* | The URL to export Prometheus remote write metrics to, for example, `https://<my-trial-instance>.splunkcloud.com:8088/services/collector`. |
| **tokenSecret** | [SecretKeyRef](about:blank#_secretkeyref) *required* | A reference to the secret and key containing the Splunk HTTP Event Collector. |
| **source** | string | The [Splunk source](https://docs.splunk.com/Splexicon%253ASource). Identifies the source of an event, that is, where the event originated. In the case of data monitored from files and directories, the source consists of the full pathname of the file or directory. In the case of a network-based source, the source field consists of the protocol and port, such as UDP:514. |
| **sourceType** | string | The [Splunk source type](https://docs.splunk.com/Splexicon%253ASourcetype). Identifies the data structure of an event. A source type determines how the Splunk platform formats the data during the indexing process. Example source types include `access_combined` and `cisco_syslog`. |
| **index** | string | The splunk index, optional name of the Splunk index targeted. |
| **tls** | [ObservabilityTls](about:blank#_observabilitytls) | TLS configuration for connections to the Splunk HTTP Event Collector. |

### <a href="about:blank#_observabilitygooglecloud"></a> ObservabilityGoogleCloud

Configuration for a Google Cloud exporter.

| Field | Type | Description |
| --- | --- | --- |
| **serviceAccountKeySecret** | [ObjectRef](about:blank#_objectref) *required* | A secret containing a Google service account JSON key, in a property called `key.json`.

The service account used must have the `roles/logging.logWriter` role if exporting logs. The `roles/monitoring.metricWriter` role if exporting metrics. The `roles/cloudtrace.agent` role if exporting traces. |

### <a href="about:blank#_observabilityazuremonitor"></a> ObservabilityAzureMonitor

Configuration for an Azure Monitor exporter.

| Field | Type | Description |
| --- | --- | --- |
| **connectionString** | string *required* | The Azure Monitor connection string, obtained from your Application Insights resource in the Azure portal. |

### <a href="about:blank#_observabilityotlphttp"></a> ObservabilityOtlpHttp

Configuration for an OpenTelemetry exporter using the OTLP HTTP protocol.

| Field | Type | Description |
| --- | --- | --- |
| **endpointBaseUrl** | string *required* | The base URL to export OTLP metrics, logs, or traces to, for example, `https://my.otlp.host:4318`. The appropriate path (`/v1/logs`, `/v1/metrics`, `/v1/traces`) will be appended automatically. |
| **encoding** | string | The encoding to use, either `proto` or `json`. Defaults to `proto`. |
| **tls** | [ObservabilityTls](about:blank#_observabilitytls) | TLS configuration for connections to the OpenTelemetry collector. |
| **headers** | [] [ObservabilityHeader](about:blank#_observabilityheader) | A list of headers to add to outgoing requests. |

### <a href="about:blank#_observabilitytls"></a> ObservabilityTls

Configuration for TLS connections to various exporters.

| Field | Type | Description |
| --- | --- | --- |
| **insecure** | boolean | If true, will not use TLS. Defaults to false. |
| **insecureSkipVerify** | boolean | If true, will not verify the certificate presented by the server it connects to. Has no effect if `insecure` is set to true. |
| **clientCertSecret** | [ObjectRef](about:blank#_objectref) | If configured, will use the TLS secret as a client certificate to authenticate outgoing connections to the server with. |
| **caSecret** | [ObjectRef](about:blank#_objectref) | If configured, will use the certificate chain defined in the TLS CA secret to verify the server certificate provided by the server. |

### <a href="about:blank#_observabilityheader"></a> ObservabilityHeader

Configuration for a header. Only one value field may be defined.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string | The name of the header. |
| **value** | string | The value for the header. Either this, or `valueFrom` may be defined, but not both. |
| **valueFrom** | [ObservabilityHeaderSource](about:blank#_observabilityheadersource) | The source of the value for the header. Either this, or `value` may be defined, but not both. |

### <a href="about:blank#_observabilityheadersource"></a> ObservabilityHeaderSource

The source for a header value.

| Field | Type | Description |
| --- | --- | --- |
| **secretKeyRef** | [SecretKeyRef](about:blank#_secretkeyref) *required* | A reference to a secret. |

### <a href="about:blank#_heapdump"></a> HeapDump

Configuration for exporting heap dumps. Exactly one of `aws` or `platformManaged` must be configured.

| Field | Type | Description |
| --- | --- | --- |
| **aws** | [HeapDumpAws](about:blank#_heapdumpaws) | If defined, heap dumps will be exported to an AWS S3 bucket. |
| **platformManaged** | [HeapDumpPlatformManaged](about:blank#_heapdumpplatformmanaged) | If defined, heap dumps will be stored using platform-managed storage. Not available in all regions. |

### <a href="about:blank#_heapdumpaws"></a> HeapDumpAws

Configuration for exporting heap dumps to an AWS S3 bucket.

| Field | Type | Description |
| --- | --- | --- |
| **bucket** | string *required* | The S3 bucket to store heap dumps in. |
| **region** | string *required* | The AWS region of the bucket. |
| **pathPrefix** | string | An optional prefix to prepend to the name of the stored object. |
| **concurrentUploads** | integer | The maximum number of concurrent uploads. Defaults to `10`. |
| **credentialsSecretRef** | [HeapDumpCredentialSecretReference](about:blank#_heapdumpcredentialsecretreference) | If set, will use the configured credentials to access AWS. Either this or `workloadIdentity` may be set, but not both. |
| **workloadIdentity** | [HeapDumpAwsWorkloadIdentity](about:blank#_heapdumpawsworkloadidentity) | If set, will use workload identity to access AWS. Either this or `credentialsSecretRef` may be set, but not both. |

### <a href="about:blank#_heapdumpplatformmanaged"></a> HeapDumpPlatformManaged

Configuration for platform-managed heap dump storage. Not available in all regions.

| Field | Type | Description |
| --- | --- | --- |
| **pathPrefix** | string | An optional prefix to prepend to the name of the stored object. |

### <a href="about:blank#_heapdumpawsworkloadidentity"></a> HeapDumpAwsWorkloadIdentity

Configuration for using AWS workload identity for heap dump uploads.

| Field | Type | Description |
| --- | --- | --- |
| **roleArnOverride** | string | If set, overrides the Role ARN configured on the service. |

### <a href="about:blank#_heapdumpcredentialsecretreference"></a> HeapDumpCredentialSecretReference

References to secrets containing AWS credentials for heap dump uploads.

| Field | Type | Description |
| --- | --- | --- |
| **awsAccessKeyId** | [SecretKeyRef](about:blank#_secretkeyref) | A reference to the secret and key containing the AWS access key ID. |
| **awsSecretAccessKey** | [SecretKeyRef](about:blank#_secretkeyref) | A reference to the secret and key containing the AWS secret access key. |

### <a href="about:blank#_tracesampling"></a> TraceSampling

Trace sampling configuration. Applies to all trace exporters.

| Field | Type | Description |
| --- | --- | --- |
| **probabilistic** | [ProbabilisticSampling](about:blank#_probabilisticsampling) | If defined, enables probabilistic (percentage-based) sampling of traces. |

### <a href="about:blank#_probabilisticsampling"></a> ProbabilisticSampling

Configuration for probabilistic trace sampling.

| Field | Type | Description |
| --- | --- | --- |
| **percentage** | string | The percentage of traces to sample. For example, `10` means 10% of traces are sampled (90% are dropped). |

### <a href="about:blank#_secretkeyref"></a> SecretKeyRef

A reference to a key within a Kubernetes secret.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string *required* | The name of the secret. |
| **key** | string *required* | The key within the secret to select. |
| **optional** | boolean | If true, the secret or its key is allowed to not exist. |

### <a href="about:blank#_objectref"></a> ObjectRef

A reference to a Kubernetes secret or other object by name.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string *required* | The name of the object. |

<!-- <footer> -->
<!-- <nav> -->
[External secret descriptor](external-secret-descriptor.html) [Service reference configuration (HOCON)](../config/reference.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->