# Akka SDK docs

## Building the docs

### Prerequisites

- **Docker**: The documentation build process uses Docker to run the Antora build inside a container. You can [download and install Docker](https://docs.docker.com/get-docker/) from the official site.

### Build Process

To build the documentation locally, use the following commands in the root directory of the repository.

1. Prepare managed sources (copies samples sources into Antora directories)

```bash
make managed
```

2. Create the full site
```bash
make local open
```

This command will execute a Docker-based build process that compiles the documentation. The generated documentation will be output to the `target/site` directory.

1. Clean site and managed sources
```bash
make clean
```

## Viewing the docs

Once the build process is complete, you can view the documentation by opening the `index.html` file located in the `target/site` directory in your web browser.

Example path:

```bash
open ../target/site/index.html
```

## Deploying the docs

### Deploying Work In Progress (WIP) to doc.akka.io/snapshots

The deployment process is automatically triggered through GitHub Actions [Deploy WIP to doc.akka.io/snapshots/](https://github.com/akka/akka-sdk/actions/workflows/docs-wip.yml) Workflow when merging to main.

The WIP documentation will be published and available for viewing at [https://doc.akka.io/snapshots/](https://doc.akka.io/snapshots).
