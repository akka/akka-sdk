# akka organizations auth

Manage authentication for your organization on Akka

## Synopsis

The `akka organizations auth` commands manage the authentication for organizations in Akka.
For example, you can configure your organization to use OpenID Connect (OIDC) for authentication.

## Options

```
  -h, --help   help for auth
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

* [akka organizations](akka_organizations.html)	 - Manage your organizations on Akka
* [akka organizations auth add](akka_organizations_auth_add.html)	 - Add an authentication domain to your organization on Akka
* [akka organizations auth list](akka_organizations_auth_list.html)	 - List the authentication domains for the current (or specified) organization
* [akka organizations auth remove](akka_organizations_auth_remove.html)	 - Remove the given authentication domain from the current (or specified) organization
* [akka organizations auth show](akka_organizations_auth_show.html)	 - Display information about the given authentication domain for the current (or specified) organization
* [akka organizations auth update](akka_organizations_auth_update.html)	 - Update an authentication domain for your organization on Akka
