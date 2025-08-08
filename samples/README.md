# Samples

## Java formatting

A shared formatting setup is configured for Java samples.

Formatting is handled by the [prettier-maven-plugin](https://github.com/HubSpot/prettier-maven-plugin), defined in the parent `pom.xml`. It is applied using the Maven profile `formatting`.

CI checks that all code is correctly formatted. If not, the build will fail.

### CI

The GitHub Actions workflow runs `prettier:check` on all Java samples. Formatting errors will be reported as part of the build.

### Plugin settings

- Line length: 94
- Ident lines with spaces, not tabs
- Number of spaces per indentation level: 2

