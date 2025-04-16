# akka services jwts

Manage JWT keys of a service.

## Options

```
  -h, --help   help for jwts
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

* [akka services](akka_services.html)	 - Manage and deploy services on Akka.
* [akka services jwts add](akka_services_jwts_add.html)	 - Add a JWT key to a service.
* [akka services jwts generate](akka_services_jwts_generate.html)	 - Generate a JWT key for a service.
* [akka services jwts list](akka_services_jwts_list.html)	 - List all JWT keys for a service.
* [akka services jwts list-algorithms](akka_services_jwts_list-algorithms.html)	 - List all the supported JWT algorithms.
* [akka services jwts remove](akka_services_jwts_remove.html)	 - Remove a JWT key from a service.
* [akka services jwts update](akka_services_jwts_update.html)	 - Update a JWT key in a service.
