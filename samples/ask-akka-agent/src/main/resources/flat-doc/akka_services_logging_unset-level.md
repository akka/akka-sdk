# akka services logging unset-level

Unset the logger level configuration.

## Synopsis

Unset the logger level configuration for a specific logger in the given service.
The level will be reverted to the parent logger configuration.
This configuration will be applied to all instances of the service.

```
akka services logging unset-level [SERVICE_NAME] [LOGGER_NAME] [flags]
```

## Examples

```

> akka service logging unset-level my-service com.example.MyClass
```

## Options

```
  -h, --help   help for unset-level
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

* [akka services logging](akka_services_logging.html)	 - Change the log level configration of a service.
