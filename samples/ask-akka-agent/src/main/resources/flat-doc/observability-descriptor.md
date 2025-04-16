# Observability Descriptor reference

## Akka Observability descriptor

An Akka observability descriptor describes how metrics, logs, and traces are exported to third party services. It is used by the `akka project observability apply` command. Exporters can be optionally defined as default exporter, meaning it will be used for  the metrics, logs, and traces, but can then be optionally overridden for each of metrics, logs, and traces.

| Field | Type | Description |
| --- | --- | --- |
| **exporter** | [ObservabilityDefault](#observabilitydefault) | The default exporter used for metrics, logs, and traces. Will be used for each unless a respective exporter in `logs` or `metrics` is defined. |
| **metrics** | [ObservabilityMetrics](#observabilitymetrics) | The exporter to use for metrics. Overrides the exporter defined in `exporter`, but just for metrics. |
| **logs** | [ObservabilityLogs](#observabilitylogs) | The exporter to use for logs. Overrides the exporter defined in `exporter`, but just for logs. |
| **traces** | [ObservabilityTraces](#observabilitytraces) | The exporter to use for traces. Overrides the exporter defined in `exporter`, but just for traces. |

### ObservabilityDefault

The default exporter configuration for metrics, logs, and traces. At most one default exporter may be configured.

| Field | Type | Description |
| --- | --- | --- |
| **kalixConsole** | object | If defined, metrics will be exported to the Akka Console. There are no configuration parameters for the console exporter, it should be declared as an empty object. |
| **otlp** | [ObservabilityOtlp](#observabilityotlp) | If defined, will export metrics, logs, and traces to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **splunkHec** | [ObservabilitySplunkHec](#observabilitysplunkhec) | If defined, will export metrics and logs to a Splunk platform instance, using the Splunk HTTP Event Collector. |
| **googleCloud** | [ObservabilityGoogleCloud](#observabilitygooglecloud) | If defined, will export metrics, logs, and traces to Google Cloud. |

### ObservabilityMetrics

The metrics exporter configuration. At most one metrics exporter may be configured. If a default exporter is configured, the exporter configured here will override that exporter for metrics.

| Field | Type | Description |
| --- | --- | --- |
| **kalixConsole** | object | If defined, metrics will be exported to the Akka Console. There are no configuration parameters for the console exporter, it should be declared as an empty object. |
| **otlp** | [ObservabilityOtlp](#observabilityotlp) | If defined, will export metrics to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **prometheuswrite** | [ObservabilityPrometheusWrite](#observabilityprometheuswrite) | If defined, will export metrics using the Prometheus remote write protocol. |
| **splunkHec** | [ObservabilitySplunkHec](#observabilitysplunkhec) | If defined, will export metrics to a Splunk platform instance, using the Splunk HTTP Event Collector. |
| **googleCloud** | [ObservabilityGoogleCloud](#observabilitygooglecloud) | If defined, will export metrics to Google Cloud. |

### ObservabilityTraces

The traces exporter configuration. At most one traces exporter may be configured. If a default exporter is configured, the exporter configured here will override the default exporter for traces.

| Field | Type | Description |
| --- | --- | --- |
| **kalixConsole** | object | If defined, metrics will be exported to the Akka Console. There are no configuration parameters for the console exporter, it should be declared as an empty object. |
| **otlp** | [ObservabilityOtlp](#observabilityotlp) | If defined, will export traces to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **googleCloud** | [ObservabilityGoogleCloud](#observabilitygooglecloud) | If defined, will export traces to Google Cloud. |

### ObservabilityLogs

The logs exporter configuration. At most one logs exporter may be configured. If a default exporter is configured, the exporter configured here will override that exporter for logs.

| Field | Type | Description |
| --- | --- | --- |
| **otlp** | [ObservabilityOtlp](#observabilityotlp) | If defined, will export logs to an OpenTelemetry collector using the OTLP gRPC protocol. |
| **splunkHec** | [ObservabilitySplunkHec](#observabilitysplunkhec) | If defined, will export logs to a Splunk platform instance, using the Splunk HTTP Event Collector. |
| **googleCloud** | [ObservabilityGoogleCloud](#observabilitygooglecloud) | If defined, will export logs to Google Cloud. |

### ObservabilityOtlp

Configuration for an OpenTelemetry exporter using the OTLP gRPC protocol.

| Field | Type | Description |
| --- | --- | --- |
| **endpoint** | string _required_ | The endpoint to export OTLP metrics, logs, or traces to, for example, `my.otlp.host:443`. |
| **tls** | [ObservabilityTls](#observabilitytls) | TLS configuration for connections to the OpenTelemetry collector. |
| **headers** | [][ObservabilityHeader](#observabilityheader) | A list of headers to add to outgoing requests. |

### ObservabilityPrometheusWrite

Configuration for a Prometheus exporter using the Prometheus remote write protocol.

| Field | Type | Description |
| --- | --- | --- |
| **endpoint** | string _required_ | The URL to export Prometheus remote write metrics to, for example, `https://my.cortex.host/api/v1/push`. |
| **tls** | [ObservabilityTls](#observabilitytls) | TLS configuration for connections to the Prometheus remote write endpoint. |
| **headers** | [][ObservabilityHeader](#observabilityheader) | A list of headers to add to outgoing requests. |

### ObservabilitySplunkHec

Configuration for a Splunk HEC exporter to export to Splunk Platform instance using the Splunk HTTP Event Collector.

| Field | Type | Description |
| --- | --- | --- |
| **endpoint** | string _required_ | The URL to export Prometheus remote write metrics to, for example, `https://<my-trial-instance>.splunkcloud.com:8088/services/collector`. |
| **tokenSecret** | [SecretKeyRef](#SecretKeyRef) _required_ | A reference to the secret and key containing the Splunk HTTP Event Collector. |
| **source** | string | The [Splunk source](https://docs.splunk.com/Splexicon%3ASource). Identifies the source of an event, that is, where the event originated. In the case of data monitored from files and directories, the source consists of the full pathname of the file or directory. In the case of a network-based source, the source field consists of the protocol and port, such as UDP:514. |
| **sourceType** | string | The [Splunk source type](https://docs.splunk.com/Splexicon%3ASourcetype). Identifies the data structure of an event. A source type determines how the Splunk platform formats the data during the indexing process. Example source types include `access_combined` and `cisco_syslog`. |
| **index**	 | string | The splunk index, optional name of the Splunk index targeted. |
| **tls** | [ObservabilityTls](#observabilitytls) | TLS configuration for connections to the Splunk HTTP Event Collector. |

### ObservabilityGoogleCloud

Configuration for a Google Cloud exporter.

| Field | Type | Description |
| --- | --- | --- |
| **serviceAccountKeySecret** | [ObjectRef](#ObjectRef) _required_ | A secret containing a Google service account JSON key, in a property called `key.json`. The service account used must have the `roles/logging.logWriter` role if exporting logs. The `roles/monitoring.metricWriter` role if exporting metrics. The `roles/cloudtrace.agent` role if exporting traces. |

### ObservabilityTls

Configuration for TLS connections to various exporters.

| Field | Type | Description |
| --- | --- | --- |
| **insecure** | boolean | If true, will not use TLS. Defaults to false. |
| **insecureSkipVerify** | boolean | If true, will not verify the certificate presented by the server it connects to. Has no effect if `insecure` is set to true. |
| **clientCertSecret** | [ObjectRef](#ObjectRef) | If configured, will use the TLS secret as a client certificate to authenticate outgoing connections to the server with. |
| **caSecret** | [ObjectRef](#ObjectRef) | If configured, will use the certificate chain defined in the TLS CA secret to verify the server certificate provided by the server. |

### ObservabilityHeader

Configuration for a header. Only one value field may be defined.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string | The name of the header. |
| **value** | string | The value for the header. Either this, or `valueFrom` may be defined, but not both. |
| **valueFrom** | [ObservabilityHeaderSource](#observabilityheadersource) | The source of the value for the header. Either this, or `value` may be defined, but not both. |

### ObservabilityHeaderSource

The source for a header value.

| Field | Type | Description |
| --- | --- | --- |
| **secretKeyRef** | [SecretKeyRef](#SecretKeyRef) _required_ | A reference to a secret. |
