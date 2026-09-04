# Releasing Akka SDK

Create a release issue:

```shell
bin/create-release-issue.sh 3.1.0
````

and follow the instructions.

## Publishing documentation hotfixes

The documentation is automatically published with regular releases.

To publish documentation of certain merged Pull Requests:
1. mark the [merged documentation PRs](https://github.com/akka/akka-sdk/pulls?q=is%3Apr+is%3Amerged+label%3Adocumentation+-label%3Adocs-published) that should be published with the `docs-publish` label,
2. check all desired PRs are on [the list](https://github.com/akka/akka-sdk/pulls?q=is%3Apr+is%3Aclosed+label%3Adocs-publish+-label%3Adocs-published),
3. trigger the [Publish docs (cherry-pick batch)](https://github.com/akka/akka-sdk/actions/workflows/docs-publish.yml) workflow,
4. review the PR it creates and **merge** it (do *not* squash — squashing drops the `cherry picked from` trailers) to the [docs-current branch](https://github.com/akka/akka-sdk/tree/docs-current),
5. the [Documentation to doc.akka.io](https://github.com/akka/akka-sdk/actions/workflows/docs-prod.yml) will trigger.

(The "Publish docs" workflow adds the `docs-published` label to the PRs it handled.)

If a cherry-pick fails, the workflow comments on the source PR, skips it, and lists it under "Skipped" in the batch PR body. Those changes will not be published until you either cherry-pick them onto `docs-current` by hand, or fix the source branch and re-run the workflow (the `docs-publish` label is still there, so the PR will be picked up on the next run).
