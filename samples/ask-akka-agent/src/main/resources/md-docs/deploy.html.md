<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Specify reference](index.html)
- [/akka.deploy](deploy.html)

<!-- </nav> -->

# /akka.deploy

Deploy the service to Akka Automated Operations.

## <a href="about:blank#_usage"></a> Usage

```none
/akka:deploy
```

## <a href="about:blank#_description"></a> Description

Builds a container image, pushes it, and deploys the service to the Akka platform. Prompts for organization and project, then seamlessly deploys with automatic rolling updates if a service is already running.

After deployment, the command verifies component health and inspects the deployed service using backoffice tools.

## <a href="about:blank#_see_also"></a> See also

- [Spec-driven development](../../sdk/spec-driven-development.html)
- [Specify command reference](index.html)
- [/akka.build](build.html)
- [/akka.inspect](inspect.html)

<!-- <footer> -->
<!-- <nav> -->
[/akka.constitution](constitution.html) [/akka.implement](implement.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->