<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Observability and monitoring](index.html)
- [View traces](traces.html)

<!-- </nav> -->

# View traces

Akka projects have the traces dashboard built-in as part of the Control Tower in the Akka Console. This is available out-of-the-box. You can find your traces here as long as you have [enabled the traces](observability-exports.html#activating_tracing) in your service.

In the top panel you have the list of traces you can inspect. When you click on one of them, the contents of the trace are displayed in the panel below. As shown in the figure.

![dashboard control tower traces screenshot](../_images/dashboard-control-tower-traces-screenshot.png)


You can filter by time to select traces in the desired time period. And further select the spans of a trace to find out more details about its attributes and resources, as shown in the figure.

![dashboard control tower span screenshot](../_images/dashboard-control-tower-span-screenshot.png)


<!-- <footer> -->
<!-- <nav> -->
[View metrics](metrics.html) [Exporting metrics, logs, and traces](observability-exports.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->