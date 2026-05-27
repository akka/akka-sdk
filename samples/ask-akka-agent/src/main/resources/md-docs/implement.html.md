<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Specify reference](index.html)
- [/akka.implement](implement.html)

<!-- </nav> -->

# /akka.implement

Generate the required code, tests, and harnesses.

## <a href="about:blank#_usage"></a> Usage

```none
/akka:implement
```

## <a href="about:blank#_description"></a> Description

Processes and executes all tasks defined in `tasks.md`, generating application code, unit tests, and integration tests. The agent iterates through compilation failures and test failures toward a working implementation.

During this step, it is normal to see the agent make mistakes and self-correct. Only intervene if you see the agent diverging from the solution over time instead of converging.

The default Akka constitution mandates both unit and integration tests, which are generated and verified during this step.

## <a href="about:blank#_see_also"></a> See also

- [Spec-driven development](../../sdk/spec-driven-development.html)
- [Specify command reference](index.html)
- [/akka.tasks](tasks.html)
- [/akka.build](build.html)

<!-- <footer> -->
<!-- <nav> -->
[/akka.deploy](deploy.html) [/akka.inspect](inspect.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->