# akka logs

Display the last few lines of logs for a specific service.

## Synopsis

Get the last few lines of logs for a specific service in the current project.
By default, includes instance and lifecycle logs.
If there’s only one pod for that service and only one container’s logs are requested, the logs from the container start will be returned, otherwise up to 100 lines per container will be returned.

```
akka logs [SERVICE] [INSTANCE] [flags]
```

## Examples

```

> akka logs my-service
2021-05-27 03:44:57.755 app[service-f97bb7497-pqmk5] itemAdded::push
2021-05-27 03:44:58.053 app[service-f97bb7497-pqmk5] return state
...
```

## Options

```
      --color            Whether color output should be used (default true)
  -f, --follow           Whether logs should be followed.
      --format string    The go template used to render JSON formatted messages. Is not used for messages that are not JSON. (default "{{ .severity | bold }}{{if .logger}} {{ .logger | abbr 30 | blue }}{{end}} {{ .message }}{{if .exception}}\n{{ .exception }}{{end}}")
  -h, --help             help for logs
      --instance         Whether instance logs should be included. Ignored if there is no SERVICE provided. (default true)
      --lifecycle        Whether lifecycle logs should be included. (default true)
      --observability    Whether logs for metrics and log exporting should be included.
      --owner string     the owner of the project to use, needed if you have two projects with the same name from different owners
      --project string   project to use if not using the default configured project
      --raw              Don't try and decode the log messages as JSON, output in their raw form.
      --region string    region to use if project has more than one region
  -t, --tail int         The maximum number of lines to fetch. Use -1 to fetch from the container start. If follow is also supplied, this will be the number of existing lines that will be output.
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

* [akka](akka.html)	 - Akka control
