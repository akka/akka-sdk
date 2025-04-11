# akka config current-context

Show the current context.

## Synopsis

The command `akka config current-context` will display the name of your currently selected configuration context.
A configuration context represents a set of configuration values intended to be used together - you may have several contexts, and switch between them with this command.

```
akka config current-context [flags]
```

## Options

```
  -h, --help   help for current-context
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

* [akka config](akka_config.html)	 - Manage configuration and context for the Akka CLI.
