# akka local

Interact with and manage Akka services running locally.

## Synopsis

The `akka local` commands help with local development by providing ways to interact with, discover and  explore Akka services and its data when running them locally.

## Options

```
  -h, --help   help for local
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
* [akka local console](akka_local_console.html)	 - Start the Akka local console.
* [akka local services](akka_local_services.html)	 - Manage Akka services locally.
