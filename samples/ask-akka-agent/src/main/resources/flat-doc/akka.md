# akka

Akka control

## Synopsis

Welcome to the Akka CLI, for more information on its usage please visit the documentation https://doc.akka.io/index.html.

## Options

```
      --cache-file string   location of cache file (default "~/.akka/cache.yaml")
      --config string       location of config file (default "~/.akka/config.yaml")
      --context string      configuration context to use
      --disable-prompt      Disable all interactive prompts when running akka commands. If input is required, defaults will be used, or an error will be raised.
                            This is equivalent to setting the environment variable AKKA_DISABLE_PROMPTS to true.
  -h, --help                help for akka
  -o, --output string       set output format to one of [text,json,go-template=] (default "text")
  -q, --quiet               set quiet output (helpful when used as part of a script)
      --timeout duration    client command timeout (default 10s)
      --use-grpc-web        use grpc-web when talking to Akka APIs. This is useful when behind corporate firewalls that decrypt traffic but don't support HTTP/2.
  -v, --verbose             set verbose output
```

## SEE ALSO

* [akka auth](akka_auth.html)	 - Manage Akka authentication.
* [akka completion](akka_completion.html)	 - Generate shell completion scripts
* [akka config](akka_config.html)	 - Manage configuration and context for the Akka CLI.
* [akka container-registry](akka_container-registry.html)	 - Manage and push service images to the Akka Container Registry.
* [akka docker](akka_docker.html)	 - Manage credentials for projects using private Docker registries.
* [akka docs](akka_docs.html)	 - Open the Akka documentation page
* [akka local](akka_local.html)	 - Interact with and manage Akka services running locally.
* [akka logs](akka_logs.html)	 - Display the last few lines of logs for a specific service.
* [akka organizations](akka_organizations.html)	 - Manage your organizations on Akka
* [akka projects](akka_projects.html)	 - Manage your Akka projects.
* [akka quickstart](akka_quickstart.html)	 - Akka quickstart project samples.
* [akka regions](akka_regions.html)	 - Manage available regions.
* [akka roles](akka_roles.html)	 - Manage the user roles for an Akka project.
* [akka routes](akka_routes.html)	 - Manage routes for your Akka project.
* [akka secrets](akka_secrets.html)	 - Manage secrets for an Akka project.
* [akka services](akka_services.html)	 - Manage and deploy services on Akka.
* [akka version](akka_version.html)	 - Print the akka CLI version
