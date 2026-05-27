<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Specify reference](index.html)
- [/akka.specify](specify.html)

<!-- </nav> -->

# /akka.specify

Supply a prompt to produce a feature specification.

## <a href="about:blank#_usage"></a> Usage

```none
/akka:specify {feature short description} - {feature specification prompt}
```

## <a href="about:blank#_description"></a> Description

Creates a new feature specification from a natural-language description. The short description is converted to kebab case and used as both the spec directory name and git branch name (e.g., `001-core-users`).

The prompt should define exclusively the *what* and *why* of the feature, excluding technical implementation details.

## <a href="about:blank#_example"></a> Example

```none
/akka:specify core users - The application manages its own users.
Users are uniquely identified by a username and authenticate via password.
Users can edit their profile and upload a small avatar image.
```

## <a href="about:blank#_see_also"></a> See also

- [Spec-driven development](../../sdk/spec-driven-development.html)
- [Specify command reference](index.html)
- [/akka.clarify](clarify.html)

<!-- <footer> -->
<!-- <nav> -->
[/akka.setup](setup.html) [/akka.tasks](tasks.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->