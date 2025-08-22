# Release Akka SDK 

### Prepare

- [ ] Make sure all important PRs have been merged
- [ ] Check that the [latest build](https://github.com/akka/akka-sdk/actions?query=branch%3Amain) successfully finished
- [ ] Make sure a version of the Akka Runtime that supports the protocol version the SDK expects has been deployed to production. You can check this on `Dependencies.scala`

You can see the Akka Runtime version on prod [on grafana](https://grafana.sre.kalix.io/d/b30d0d8e-3894-4fbf-9627-9cb6088949ee/prod-kalix-metrics?orgId=1) or using [various other methods](https://github.com/lightbend/kalix/wiki/Versioning-and-how-to-determine-what-version-is-running).

### Cutting the release 

- [ ] Update the "Change date" on [the license](../blob/main/LICENSE#L9) to release date plus three years
- [ ] Use the "Generate release notes" button to create [a new release](https://github.com/akka/akka-sdk/releases/new) with the appropriate tag.
    - Review the generated notes and "Publish release"
    - CI will automatically publish to the repository based on the tag
    - CI will update the docs/kalix-current branch

### Update to the latest version
 
- [ ] Review and merge PR created by bot (should appear [here](https://github.com/akka/akka-sdk/pulls?q=is%3Apr+is%3Aopen+auto+pr+)). While reviewing confirm the release version is updated for:
    - `version` in the `samples/*/pom.xml` files
    - `version` in `akka-javasdk-parent/pom.xml`
    - `akka-javasdk.version` in the `akka-javasdk-parent/pom.xml`

### Publish latest docs
- [ ] Add a summary of relevant changes into `docs/src/modules/reference/pages/release-notes.adoc`
- [ ] Create a PR and merge `main` into `docs-current` (do not squash)
    - Note that the PR will be pretty big normally, not only involving documentation files.

### Update samples
- [ ] Run https://github.com/akka/akka-sdk/actions/workflows/pr-akka-samples.yml from the release tag 
- [ ] Merge auto-PRs in akka-samples https://github.com/orgs/akka-samples/repositories?q=sort%3Aname-asc
 
### Announcements

- [ ] Add a summary of relevant changes and a link to the release notes into [Akka Release Notes aggregation](https://docs.google.com/document/d/1Q0yWZssJHhF9oOKMW1yHq-QCyXJ-Ej8DeNuim4_QN6w/edit?usp=sharing)
- [ ] Close this issue
