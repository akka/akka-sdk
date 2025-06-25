<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Integrating with CI/CD tools](index.html)
- [CI/CD with GitHub Actions](github-actions.html)

<!-- </nav> -->

# CI/CD with GitHub Actions

Use the Akka [setup-akka-cli-action](https://github.com/akka/setup-akka-cli-action) GitHub Action to use GitHub Actions with your Akka project. The action supports commands for installing, authenticating, and invoking the Akka CLI. Releases are tracked [on the GitHub releases page](https://github.com/lightbend/setup-akka-action/releases).

## <a href="about:blank#_prerequisites"></a> Prerequisites

To use the Akka GitHub Action, you’ll need to:

- Create a [service token](index.html#create_a_service_token) for your project
- Get the UUID of your project, which can be obtained by running `akka projects list`

## <a href="about:blank#_configure_variables"></a> Configure variables

The GitHub Action uses two required variables to authenticate and set the project you want to work on correctly:

- `AKKA_TOKEN`: The Akka service token
- `AKKA_PROJECT_ID`: The project ID for the Akka project you’re using
These variables should be configured as [secrets](https://docs.github.com/en/actions/reference/encrypted-secrets#creating-encrypted-secrets-for-a-repository) for your repository.

## <a href="about:blank#_create_a_workflow"></a> Create a workflow

Follow these steps to create a workflow to invoke the GitHub Action for your project:

1. Create a folder named `.github` at the root of the project folder.
2. Create a file named `config.yml` in the `.github` folder.
3. Open `config.yml` for editing and add:

```yaml
name: akka

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Install Akka CLI
        uses: akka/setup-akka-cli-action@v1
        with:
          token: ${{ secrets.AKKA_TOKEN }} // (1)
          project-id: ${{ secrets.AKKA_PROJECT_ID }} // (2)
      - name: List services // (3)
        run: akka service list // (4)
```

| **1** | The Akka authentication token. |
| **2** | The UUID of the project to which the service belongs. |
| **3** | A unique name for this workflow step. The example lists Akka services. |
| **4** | The command to execute. |

<!-- <footer> -->
<!-- <nav> -->
[Integrating with CI/CD tools](index.html) [CLI](../cli/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->