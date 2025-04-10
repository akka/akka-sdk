# Streaming

In many cases, Akka takes care of streaming and is using end-to-end backpressure automatically. Akka will also use the event journal or message brokers as durable buffers to decouple producers and consumers. You would typically only have to implement the functions to operate on the stream elements. For example:

* Views are updated asynchronously from a stream of events. You implement the update handler, which is invoked for each event.
* Views can stream the query results, and the receiver demands the pace.
* Consumers process a stream of events. You implement a handler to process each event. Same approach when the source is an entity within the service, another service, or a message broker topic.
* Consumers can produce events to other services or publish to a message broker topic. The downstream consumer or publisher defines the pace.

## Using Akka Streams

Sometimes, the built-in streaming capabilities mentioned above are not enough for what you need, and then you can use Akka Streams. A few examples where Akka Streams would be a good solution:

* Streaming from [Endpoints](http-endpoints.adoc#_advanced_http_requests_and_responses)
* For each event in a [Consumer](consuming-producing.adoc) you need to materialize a finite stream to perform some actions in a streaming way instead of composing those actions with `CompletionStage` operations.
  * the stream can be run from a [Consumer](consuming-producing.adoc) event handler
  * e.g. for each event, download a file from AWS S3, unzip, for each row send a command to entity
  * e.g. for each event, stream file from AWS S3 to Azure Blob
* Streams that are continuously running and are executed per service instance.
  * the stream can be started from the [Setup](setup-and-dependency-injection.adoc#_service_lifecycle)
  * e.g. integration with AWS SQS

For running Akka Streams you need a so-called materializer, which can be injected as a constructor parameter of the component, see [dependency injection](setup-and-dependency-injection.adoc#_dependency_injection).

You find more information about Akka Streams in the [Akka libraries documentation, window="new"](https://doc.akka.io/libraries/akka-core/current/stream/stream-introduction.html). Many streaming connectors are provided by [Alpakka, window="new"](https://doc.akka.io/libraries/alpakka/current/).
