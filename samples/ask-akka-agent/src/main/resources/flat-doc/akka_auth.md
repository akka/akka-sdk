# akka auth

Manage Akka authentication.

## Synopsis

The `akka auth` commands allow you to _authorize_ your command-line client against your account on Akka.
When authorized, you can use the CLI to perform the same operations available in the web interface.

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

* [akka](akka.html)	 - Akka control
* [akka auth container-registry](akka_auth_container-registry.html)	 - Manage configuration for Akka Container Registry.
* [akka auth current-login](akka_auth_current-login.html)	 - Get details for the current logged in user.
* [akka auth login](akka_auth_login.html)	 - Log in to Akka.
* [akka auth logout](akka_auth_logout.html)	 - Log out the current user.
* [akka auth signup](akka_auth_signup.html)	 - Open the registration page.
* [akka auth tokens](akka_auth_tokens.html)	 - Manage Akka authentication tokens for your user.
* [akka auth use-token](akka_auth_use-token.html)	 - Login using a token.
