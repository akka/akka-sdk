# JVM SDKs docs


## Building docs

To build the docs, run `make` in the `docs` directory:

```
make
```

Dynamically-generated and managed sources will be created in `build/src/managed`.

For quick iteration of changes you can use a partial build:

```
make examples dev-html
```

## Deploying the docs

### Deploying Work In Progress (WIP) to doc.akka.io/snapshots

The deployment process is intended to be manually triggered through GitHub Actions.

#### 1. Manually trigger the Workflow

To start the deployment workflow:

1. Navigate to the GitHub Actions tab for this repository.
2. Go to the [Deploy WIP to doc.akka.io/snapshots/](https://github.com/lightbend/akka-javasdk/actions/workflows/docs-wip.yml) Workflow.
3. Click **Run workflow** to manually start the deployment.

#### Post-Deployment

Once the deployment process is complete, the WIP documentation will be published and available for viewing at [https://doc.akka.io/snapshots](https://doc.akka.io/snapshots).
