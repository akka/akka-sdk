# akka projects new

Create a new Akka project.

## Synopsis

The `akka project new _name_ _[description]_ --region _region_ --organization _organization_` command will create a new project in the chosen organization, giving it the name _name_ and the optional description _description_.
It will be created in the region _region_.

Project names should describe the project so that you and other project members can identify it and its purpose.

Project names must conform to the following:

* Up to 63 characters long
* Can include:
  * lowercase letters
  * numbers
  * hyphens (-)
* Must not:
  * start or end with hyphens (-)
* Cannot include:
  * underscores (_)
  * spaces
  * non-alphanumeric characters

Use `akka regions list` to get a list of possible regions.

```
akka projects new NAME [DESCRIPTION] --region REGION [flags]
```

## Options

```
  -h, --help                  help for new
      --organization string   organization which will own the new project
      --region string         the region where the project gets created
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

* [akka projects](akka_projects.html)	 - Manage your Akka projects.
