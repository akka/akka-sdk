# akka projects

Manage your Akka projects.

## Synopsis

The `akka projects` commands manipulate the projects in your Akka account, where a project is a collection of services.
A project can be deployed to one or more regions.
For more details on projects, see https://doc.akka.io/operations/projects/index.html

## Options

```
  -h, --help   help for projects
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
* [akka projects config](akka_projects_config.html)	 - Manage project configs.
* [akka projects delete](akka_projects_delete.html)	 - Delete a project.
* [akka projects get](akka_projects_get.html)	 - Get project information.
* [akka projects hostnames](akka_projects_hostnames.html)	 - Manage hostnames for your Akka project.
* [akka projects list](akka_projects_list.html)	 - List all projects.
* [akka projects new](akka_projects_new.html)	 - Create a new Akka project.
* [akka projects observability](akka_projects_observability.html)	 - Manage the observability settings for your Akka project.
* [akka projects open](akka_projects_open.html)	 - Open the current project in the console.
* [akka projects regions](akka_projects_regions.html)	 - Manage the regions assigned to your Akka project.
* [akka projects tokens](akka_projects_tokens.html)	 - Manage your Akka project service tokens.
* [akka projects update](akka_projects_update.html)	 - Update project attributes such as name and description.
