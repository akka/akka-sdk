<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Specify reference](index.html)
- [/akka.build](build.html)

<!-- </nav> -->

# /akka.build

Build, test, and run the service locally.

## <a href="about:blank#_usage"></a> Usage

```none
/akka:build
```

## <a href="about:blank#_description"></a> Description

Provides a full local development loop with the following capabilities:

- Pre-existing services are shut down
- Anything using the target port is shut down
- The service is recompiled and all tests are run
- The service is launched
- The service is exercised through real endpoints to verify functionality
- The service remains running for manual exercise
- If documents change, the service is automatically recompiled and restarted

## <a href="about:blank#_see_also"></a> See also

- [Spec-driven development](../../sdk/spec-driven-development.html)
- [Specify command reference](index.html)
- [/akka.implement](implement.html)
- [/akka.inspect](inspect.html)

<!-- <footer> -->
<!-- <nav> -->
[/akka.analyze](analyze.html) [/akka.checklist](checklist.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->