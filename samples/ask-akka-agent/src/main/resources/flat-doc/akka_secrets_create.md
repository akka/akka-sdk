# akka secrets create

Create or update a secret in the current project.

## Synopsis

The `akka secrets create` command creates or updates a secret for the currently configured project.

## Options

```
  -h, --help   help for create
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

* [akka secrets](akka_secrets.html)	 - Manage secrets for an Akka project.
* [akka secrets create asymmetric](akka_secrets_create_asymmetric.html)	 - Create or update an asymmetric key secret.
* [akka secrets create generic](akka_secrets_create_generic.html)	 - Create or update a generic secret.
* [akka secrets create symmetric](akka_secrets_create_symmetric.html)	 - Create or update a symmetric key secret.
* [akka secrets create tls](akka_secrets_create_tls.html)	 - Create or update a TLS secret.
* [akka secrets create tls-ca](akka_secrets_create_tls-ca.html)	 - Create or update a TLS CA secret.
