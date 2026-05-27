<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Specify reference](index.html)
- [/akka.inspect](inspect.html)

<!-- </nav> -->

# /akka.inspect

Inspect a running service’s runtime state against the specification.

## <a href="about:blank#_usage"></a> Usage

```none
/akka:inspect
```

## <a href="about:blank#_description"></a> Description

Inspects a running service at runtime by exercising API endpoints, verifying internal state through backoffice tools, and validating the UI through browser tools — all driven by the feature specification.

The inspection workflow:

1. Verifies the service is running and a feature spec exists
2. Extracts API endpoints and entities from the spec
3. Exercises API endpoints with test requests
4. Verifies entity state via backoffice tools
5. Validates the UI in the browser
6. Summarizes findings
7. Offers next steps
Works with both locally running services (after `/akka.build`) and deployed services (after `/akka.deploy`).

## <a href="about:blank#_see_also"></a> See also

- [Spec-driven development](../../sdk/spec-driven-development.html)
- [Specify command reference](index.html)
- [/akka.build](build.html)
- [/akka.deploy](deploy.html)

<!-- <footer> -->
<!-- <nav> -->
[/akka.implement](implement.html) [/akka.issues](issues.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->