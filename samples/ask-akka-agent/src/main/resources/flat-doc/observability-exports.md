# Exporting metrics, logs, and traces

Akka supports exporting metrics, logs, and traces to a variety of different destinations for reporting, long term storage and alerting. Metrics, logs, and traces can either be exported to the same location, by configuring a default exporter, or to different locations by configuring separate logging, metrics, and tracing exporters. Only one exporter for each may be configured.

Observability configuration is configured per Akka project. All services in that project will use the same observability config. Configuration can either be done by specifying an observability descriptor, or by running CLI commands.

When updating observability configuration, the changes will not take effect until a service is restarted.

## Working with observability descriptors

The Akka observability descriptor allows observability export configuration to be specified in a YAML file, for versioning and reuse across projects and environments.

### Exporting the observability configuration

To export the current configuration, run:

```command line
akka project observability export
```

This will write the observability YAML descriptor out to standard out. If preferred, the `-f` argument can be used to specify a file to write the descriptor out to.

```yaml
exporter:
  kalixConsole: {}
logs: {}
metrics: {}
traces: {}
```

### Updating the observability configuration

To update the current configuration, run:

```command line
akka project observability apply -f observability.yaml
```

Where `observability.yaml` is the path to the YAML descriptor.

### Editing the observability configuration in place

If you just want to edit the observability configuration for your project, without exporting and then applying it again, run:

```command line
akka project observability edit
```

This will open the observability descriptor in a text editor. After saving and exiting the editor, the saved descriptor will be used to update the configuration.

<dl><dt><strong>📌 NOTE</strong></dt><dd>

After updating your observability configuration, you will need to restart a service to apply the new configuration. Akka automatically makes that a rolling restart.
</dd></dl>

### Activating tracing (Beta)

The generation of traces is disabled by default. To enable it you need to set [telemetry/tracing/enabled](reference:descriptors/service-descriptor.adoc#_servicespec) to `true` in the service descriptor. Like the following:

```yaml
name: <<service-name>>
service:
  telemetry:
    tracing:
      enabled: true
  image: <<docker-image>>
```

[Deploy the service with the descriptor](operations:services/deploy-service.adoc#apply).

Once this is set, Akka will start generating [spans, window="new"](https://opentelemetry.io/docs/concepts/observability-primer/#spans) for the following components: Event Sourced Entities, Key Value Entities, Endpoints, Consumers, and Views.

Another possible use is to add a `traceparent` header when calling your Akka endpoint. In this case Akka will propagate this trace parent as the root of the subsequent interaction with an Akka component. Linking each Akka call to that external trace parent.

#### Setting sampling

By default, Akka filters traces and exports them to your Akka Console. You’ll get 1% of the traces produced by your services. You can adjust this percentage according to your needs.

This filtering is known as [head sampling, window="new"](https://opentelemetry.io/docs/concepts/sampling/#head-sampling). To configure it, you need to set your observability descriptor as follows and [update the observability configuration](#updating-the-observability-configuration):

```yaml
traces:
  sampling:
    probabilistic:
      percentage: "2"
```

With this configuration, 2% of the traces are sent while the 98% are filtered. More on how much sampling [here, window="new"](https://opentelemetry.io/docs/concepts/sampling/#when-to-sample).

## Updating the configuration using commands

The Akka observability configuration can also be updated using commands. To view a human-readable summary of the observability configuration, run:

```command line
akka project observability get
```

To change configuration, the `set` command can be used. To change the default exporter for both logs and metrics, use `set default`, to change the exporter just for metrics, use `set metrics`, and to change the exporter just for logs, use `set logs`.

When using the `set` command, the default, logs or metrics exporter configuration will be completely replaced. It’s important to include the full configuration for that exporter when you run the command. For example, if you run `akka project observability set default otlp --endpoint otlp.example.com:4317`, and then you want to add a header, you must include the `--endpoint` flag when setting the header.

## Supported exporters

Akka supports a number of different destinations to export metrics, logs, and traces to. The example commands and configuration below will show configuring the default exporter, if the exporter supports metrics, logs, and traces, or the metrics, logs, or traces as applicable if not.

### Akka Console

The Akka Console is the default destination for metrics and traces. It provides a built-in, short term time series database for limited dashboards displayed in the Akka Console. To configure it:

* **Descriptor**

  ```yaml
  default:
    kalixConsole: {}
  ```
* **CLI**

  ```command line
  akka project observability set default akka-console
  ```

### OTLP

OTLP is the gRPC based protocol used by OpenTelemetry. It is supported for logs,  metrics, and traces. The primary piece of configuration it needs is an endpoint. For example:

* **Descriptor**

  ```yaml
  default:
    otlp:
      endpoint: otlp.example.com:4317
  ```
* **CLI**

  ```command line
  akka project observability set default otlp --endpoint otlp.example.com:4317
  ```

In addition, the OTLP exporter supports [TLS configuration](#tls-configuration) and [custom headers](#custom-headers). A full reference of configuration options is available in [the reference documentation](reference:descriptors/observability-descriptor.adoc#_observabilityotlp).

### Prometheus Remote Write

The Prometheus remote write protocol is supported for exporting metrics. It is generally used to write metrics into Cortex and similar long term metrics databases for Prometheus. The primary piece of configuration it needs is an endpoint. For example:

* **Descriptor**

  ```yaml
  metrics:
    prometheuswrite:
      endpoint: https://prometheus.example.com/api/v1/push
  ```
* **CLI**

  ```command line
  akka project observability set metrics prometheus --endpoint https://prometheus.example.com/api/v1/push
  ```

In addition, the Prometheus exporter supports [TLS configuration](#tls-configuration) and [custom headers](#custom-headers). A full reference of configuration options is available in [the reference documentation](reference:descriptors/observability-descriptor.adoc#_observabilityprometheuswrite).

### Splunk HEC

The Splunk HTTP Event Collector protocol is supported for exporting both metrics and logs. It is used to export to the Splunk platform. It needs an endpoint and a splunk token. The Splunk token must be configured as a [Akka secret](operations:projects/secrets.adoc), which then gets referenced from the observability configuration.

* **Descriptor**

  ```yaml
  default:
    splunkHec:
      endpoint: https://<my-trial-instance>.splunkcloud.com:8088/services/collector
      tokenSecret:
        name: my-splunk-token
        key: token
  ```
* **CLI**

  ```command line
  akka project observability set default splunk-hec \
    --endpoint https://<my-trial-instance>.splunkcloud.com:8088/services/collector \
    --token-secret-name my-splunk-token --token-secret-key token
  ```

In addition, the Splunk HEC exporter supports [TLS configuration](#tls-configuration). A full reference of configuration options is available in [the reference documentation](reference:descriptors/observability-descriptor.adoc#_observabilitysplunkhec).

### Google Cloud

Google Cloud is supported for exporting logs, metrics, and traces. The primary piece of configuration it needs is a service account JSON key. The service account associated with the key must have the following IAM roles:

* If exporting metrics to Google Cloud, it must have the `roles/monitoring.metricWriter` role.
* If exporting logs to Google Cloud, it must have the `roles/logging.logWriter` role.

To create such a service account and key using the `gcloud` command, assuming you are logged in and have a Google project configured:

1. Create a service account. In this example, we’ll call it `akka-exporter`.

   ```shellscript
   gcloud iam service-accounts create akka-exporter
   ```
2. Grant the metrics and logging roles, as required, to your service account. Substitute your project ID for `<gcp-project-id>`.

   ```shellscript
   gcloud projects add-iam-policy-binding <gcp-project-id> \
       --member "serviceAccount:akka-exporter@<gcp-project-id>.iam.gserviceaccount.com" \
       --role "roles/monitoring.metricWriter"
   gcloud projects add-iam-policy-binding <gcp-project-id> \
       --member "serviceAccount:akka-exporter@<gcp-project-id>.iam.gserviceaccount.com" \
       --role "roles/logging.logWriter"
   ```
3. Generate a key file for your service account, we’ll place it into a file called `key.json`.

   ```shellscript
   gcloud iam service-accounts keys create key.json \
       --iam-account akka-exporter@<gcp-project-id>.iam.gserviceaccount.com
   ```
4. Place the key file in an Akka secret, we’ll call the secret `gcp-credentials`. The key for key file must be `key.json`.

   ```shellscript
   akka secret create generic gcp-credentials \
     --from-file key.json=key.json
   ```

Now that you have configured service account, granted it the necessary roles, created a service account key and placed it into an Akka secret, you can now configure your observability configuration to export to Google Cloud:

* **Descriptor**

  ```yaml
  default:
    googleCloud:
      serviceAccountKeySecret:
        name: gcp-credentials
  ```
* **CLI**

  ```command line
  akka project observability set default google-cloud --service-account-key-secret gcp-credentials
  ```

## Common exporter configuration

### TLS configuration

TLS configuration for multiple different exporters can be configured. To turn off TLS altogether, the `insecure` property can be set to `true`:

* **Descriptor**

  ```yaml
  default:
    otlp:
      endpoint: otlp.example.com:4137
      tls:
        insecure: true
  ```
* **CLI**

  ```command line
  akka project observability set default otlp \
    --endpoint otlp.example.com:4137 --insecure
  ```

To skip verifying server certificates, use insecure skip verify:

* **Descriptor**

  ```yaml
  default:
    otlp:
      endpoint: otlp.example.com:4137
      tls:
        insecureSkipVerify: true
  ```
* **CLI**

  ```command line
  akka project observability set default otlp \
    --endpoint otlp.example.com:4137 --insecure-skip-verify
  ```

To specify a custom CA to verify server certificates, use the server CA property, pointing to an Akka CA secret:

* **Descriptor**

  ```yaml
  default:
    otlp:
      endpoint: otlp.example.com:4137
      tls:
        caSecret:
          name: my-ca
  ```
* **CLI**

  ```command line
  akka project observability set default otlp \
    --endpoint otlp.example.com:4137 --server-ca-secret my-ca
  ```

To specify a client certificate to use to authenticate with a remote server, you can use the client certificate property, pointing to an Akka TLS secret:

* **Descriptor**

  ```yaml
  default:
    otlp:
      endpoint: otlp.example.com:4137
      tls:
        clientCertSecret:
          name: my-client-certificate
  ```
* **CLI**

  ```command line
  akka project observability set default otlp \
    --endpoint otlp.example.com:4137 \
    --client-cert-secret my-client-certificate
  ```

### Custom headers

Custom headers may be configured for a number of exporters. Headers can either have static values, configured directly in the observability configuration, or they can reference secrets.

To specify a static header:

* **Descriptor**

  ```yaml
  default:
    otlp:
      endpoint: otlp.example.com:4137
      headers:
      - name: X-My-Header
        value: some-value
  ```
* **CLI**

  ```command line
  akka project observability set default otlp \
    --endpoint otlp.example.com:4137 \
    --header X-My-Header=some-value
  ```

To set a header value from a secret:

* **Descriptor**

  ```yaml
  default:
    otlp:
      endpoint: otlp.example.com:4137
      headers:
      - name: X-Token
        valueFrom:
          secretKeyRef:
            name: my-token
            key: token
  ```
* **CLI**

  ```command line
  akka project observability set default otlp \
    --endpoint otlp.example.com:4137 \
    --header-secret X-Token=my-token/token
  ```

## Debugging observability configuration

After updating your observability configuration, you will need to restart a service to use it. This can be done by running the `akka service restart` command. Once the service has been restarted, if there are any issues, you can check the observability agent logs, by running the following command:

```command line
akka logs <service-name> --instance=false --observability
```

This will show the observability agent logs for all instances of the service. Any errors that the agent encountered will be displayed here.

## See also

* [`akka project observability` commands](reference:cli/akka-cli/akka_projects_observability.adoc#_see_also)
