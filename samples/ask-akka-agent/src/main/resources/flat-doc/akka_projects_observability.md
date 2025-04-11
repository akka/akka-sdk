# akka projects observability

Manage the observability settings for your Akka project.

## Synopsis

The `akka project observability` command manages the observability settings for your Akka project, allowing you to configure exporters for metrics, logs, and traces so you can ship those to your observability platform of choice.
These observability settings are applied to all services in your project.

## Options

```
  -h, --help   help for observability
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

* [akka projects](akka_projects.html)	 - Manage your Akka projects.
* [akka projects observability apply](akka_projects_observability_apply.html)	 - Apply an observability descriptor to the currently configured project.
* [akka projects observability config](akka_projects_observability_config.html)	 - Manage the observability configuration for your Akka project.
* [akka projects observability edit](akka_projects_observability_edit.html)	 - Edit observability configuration for your Akka project.
* [akka projects observability export](akka_projects_observability_export.html)	 - Export an observability descriptor from the currently configured project.
* [akka projects observability get](akka_projects_observability_get.html)	 - Get observability configuration for your Akka project.
* [akka projects observability set](akka_projects_observability_set.html)	 - Set the observability settings for your Akka project.
* [akka projects observability unset](akka_projects_observability_unset.html)	 - Unset the observability settings for your Akka project.
