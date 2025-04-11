# Viewing data

It is often helpful to see the data of your running service for debugging and administration purposes. With the Akka CLI, `akka`, you can view the entities that are stored in the durable state store. That allows you to see both the current state and the received events.

## Listing components

All the components in an entity can be listed by running:

```bash
akka service components list my-service
```

This will show you the name of the component (which equals the fully qualified name of the gRPC service that it implements), the type of component (such as key value entity, event sourced entity, view, action), and if applicable, the entity type name.

## Listing entity ids

For components that support listing entity ids, you can list all the ids of all the entities:

```bash
akka service components list-entity-ids my-service com.example.MyEntity
```

For this command you need to pass the name of the service and the component. In the example above, the name of the service is `my-service` and the name of the component is `com.example.MyEntity`. The component name is the fully qualified name of the gRPC service for that component.

The results are paged. To learn about interacting with pages, see [Paging results](#paging-results).

## Listing event sourced entity events

For event sourced entities, you can list all the events for a given entity id:

```bash
akka service components list-events my-service com.example.MyEntity entity-id
```

This will output a list of all the events for the entity, showing the sequence number, timestamp, the type of the event, and a preview of the event data in JSON format.

You can also output the full event data in JSON format:

```bash
akka service components list-events my-service com.example.MyEntity entity-id -o json
```

This outputs the list of events as a JSON array, and contains the full event data as JSON. Note that, if you’re storing your events in protobuf format, `akka` will decode the events to JSON in order to render the output. You can use the `--raw` flag to skip decoding and see the event data as a base64 encoded string:

```bash
akka service components list-events my-service com.example.MyEntity entity-id -o json --raw
```

The results are paged. To learn about interacting with pages, see [Paging results](#paging-results).

## Paging results

By default, commands that return paged results will display a maximum of 100 results. This can be controlled using the `--page-size` argument, however, 100 is the maximum size of a page.

When the results returned are only a partial page, the output will contain a token that can be used to fetch the next page:

```bash
$ akka service components list-entity-ids customer-registry \
   customer.api.CustomerService --page-size 3
ID
alice
barney
fred
There are more results, fetch by passing --page-token CAMaIAocY3VzdG9tZXIuYXBpLkN1c3RvbWVyU2VydmljZRAD
```

This token can then be used to fetch the next page of results:

```bash
$ akka service components list-entity-ids customer-registry \
    customer.api.CustomerService --page-size 3 \
    --page-token CAMaIAocY3VzdG9tZXIuYXBpLkN1c3RvbWVyU2VydmljZRAD
ID
james
john
```

You can also supply the `--interactive` flag to instruct `akka` to allow you to page through results interactively:

```bash
$ akka service components list-entity-ids customer-registry \
    customer.api.CustomerService --page-size 3 --interactive
ID
alice
barney
fred

Press enter for next page...

ID
james
john
```

## See also

* [`akka service components` commands](reference:cli/akka-cli/akka_services_components.adoc#_see_also)
