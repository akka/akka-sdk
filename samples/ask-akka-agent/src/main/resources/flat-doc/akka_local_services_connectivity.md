# akka local services connectivity

Summarize all services connectivity and status.

## Synopsis

The `akka local services connectivity` command summarizes of all service connectivity under your current project.

```
akka local services connectivity [flags]
```

## Options

```
      --all-regions      run the command on all regions
  -h, --help             help for connectivity
      --owner string     the owner of the project to use, needed if you have two projects with the same name from different owners
      --project string   project to use if not using the default configured project
      --region string    region to use if project has more than one region
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
