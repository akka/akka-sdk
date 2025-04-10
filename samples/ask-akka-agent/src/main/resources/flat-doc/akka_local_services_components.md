# akka local services components

Inspect components of a service.

## Synopsis

The `akka service components` commands provides a way to inspect the components of a service but also to peek at the data stored in the different entities.

## Options

```
  -h, --help   help for components
```

## Options inherited from parent commands

```
      --cache-file string   location of cache file (default "~/.akka/cache.yaml")
      --config string       location of config file (default "~/.akka/config.yaml")
      --context string      configuration context to use
      --disable-prompt      Disable all interactive prompts when running akka commands. If input is required, defaults will be used, or an error will be raised.
                            This is equivalent to setting the environment variable AKKA_DISABLE_PROMPTS to true.
  -o, --output string       set output format to one of [text,json,go-template=] (default "text")
  -q, --quiet               set quiet output (helpful when used as part of a script)
      --timeout duration    client command timeout (default 10s)
      --use-grpc-web        use grpc-web when talking to Akka APIs. This is useful when behind corporate firewalls that decrypt traffic but don't support HTTP/2.
  -v, --verbose             set verbose output
```

## SEE ALSO

* [akka local services](akka_local_services.html)	 - Manage Akka services locally.
* [akka local services components get-state](akka_local_services_components_get-state.html)	 - List the state for the given service, stateful component name and ID
* [akka local services components get-workflow](akka_local_services_components_get-workflow.html)	 - Get workflow execution details for the specified service, workflow component name and id
* [akka local services components list](akka_local_services_components_list.html)	 - List the components served by this service.
* [akka local services components list-events](akka_local_services_components_list-events.html)	 - List events from the Event Sourced Entity for the given service, component and entity id
* [akka local services components list-ids](akka_local_services_components_list-ids.html)	 - List the IDs for the given named stateful component served by this service.
* [akka local services components list-timers](akka_local_services_components_list-timers.html)	 - List the timers registered in this service.
