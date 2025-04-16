# akka routes

Manage routes for your Akka project.

## Synopsis

The `akka routes` commands manipulate ingress routes for your Akka project.
Routes are used to map incoming requests to services in your project.
Each route has a hostname and a set of paths that map to services.

## Examples

```

> akka route create my-ecommerce-project \
  --hostname ecommerce.acme.org \
  --path /carts=shopping-cart \
  --path /products=product-info

For other examples, see https://doc.akka.io/operations/services/invoke-service.html#_managing_routes .
```

## Options

```
  -h, --help   help for routes
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
* [akka routes create](akka_routes_create.html)	 - Create a route.
* [akka routes delete](akka_routes_delete.html)	 - Delete a route
* [akka routes edit](akka_routes_edit.html)	 - Edit a route
* [akka routes export](akka_routes_export.html)	 - Export a single route as YAML
* [akka routes get](akka_routes_get.html)	 - Get a single route
* [akka routes list](akka_routes_list.html)	 - List all routes.
* [akka routes update](akka_routes_update.html)	 - Update a route
