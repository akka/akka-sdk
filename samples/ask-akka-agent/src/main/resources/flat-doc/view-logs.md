# View logs

Akka provides logs that you can view in the Console or access with the CLI. For each service instance we aggregate a maximum of 1MB of log data. You can capture all log output by attaching a logging provider, such as Google Cloud’s operations suite (formerly Stackdriver), as described [here](observability-and-monitoring/observability-exports.adoc#_google_cloud).

## Aggregated logs

To view aggregated logs:

* **Browser**

  1. From the project **Dashboard**, select a deployed service.
  2. From the service **Overview** page, select **Logs** from the top tab or from the left navigation menu.
  The **Logs** table displays logging output, which you can filter with the control on top.
* **CLI**\
With a command window set to your project, use the `akka logs` command to view the logs for a running service:

  ```command line
  akka logs <<service-name>>
  ```

## Exporting logs

Logs can be exported for searching, reporting, alerting and long term storage by configuring the Akka observability configuration for your project. See [here](observability-and-monitoring/observability-exports.adoc) for detailed documentation.

## Correlating logs

You can correlate your log statements, those that you write in your application, by adding the MDC pattern `%mdc{trace_id}` to your log file when tracing is [enabled](operations:observability-and-monitoring/observability-exports.adoc#activating_tracing). Like the following:

**logback.xml**

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} trace_id: %mdc{trace_id} - %msg%n</pattern>
        </encoder>
    </appender>
...
</configuration>
```

This way, the trace ID that’s passed through your components will be added to your logs. For more information on tracing, click [here](operations:observability-and-monitoring/traces.adoc).

## See also

* [`akka logs` commands](reference:cli/akka-cli/akka_logs.adoc#_see_also)
