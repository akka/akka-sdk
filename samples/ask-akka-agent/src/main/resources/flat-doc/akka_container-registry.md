# akka container-registry

Manage and push service images to the Akka Container Registry.

## Synopsis

The Akka Container Registry (ACR) can be used by all users to deploy their services.
ACR makes service container images available to Akka in all regions automatically.

**📌 NOTE**\
to use an external Docker registry instead, refer to the documentation https://doc.akka.io/operations/projects/external-container-registries.html .

## Options

```
  -h, --help   help for container-registry
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
* [akka container-registry delete-image](akka_container-registry_delete-image.html)	 - Delete image from the Akka Container Registry.
If no tag is provided, it deletes all tags.
* [akka container-registry list](akka_container-registry_list.html)	 - List the Akka Container Registry and region.
* [akka container-registry list-images](akka_container-registry_list-images.html)	 - list images from the Akka Container Registry.
* [akka container-registry list-tags](akka_container-registry_list-tags.html)	 - List all images tags.
* [akka container-registry print](akka_container-registry_print.html)	 - Print the path of the Akka Container Registry.
* [akka container-registry push](akka_container-registry_push.html)	 - Push an Akka service image for a particular Akka project.
