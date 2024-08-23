# Release Akka Platform JVM SDK 

### Prepare

- [ ] Make sure all important PRs have been merged
- [ ] Check that the [latest build](https://github.com/lightbend/akka-platform-jvm-sdk//actions?query=branch%3Amain) successfully finished
- [ ] Make sure a version of the Kalix Runtime that supports the protocol version the SDK expects has been deployed to production. You can check this on `Dependencies.scala`

You can see the Kalix Runtime version on prod [on grafana](https://grafana.sre.kalix.io/d/b30d0d8e-3894-4fbf-9627-9cb6088949ee/prod-kalix-metrics?orgId=1) or using [various other methods](https://github.com/lightbend/kalix/wiki/Versioning-and-how-to-determine-what-version-is-running).

### Cutting the release 

- [ ] Update the "Change date" on [the license](../blob/main/LICENSE#L9) to release date plus three years
- [ ] Use the "Generate release notes" button to create [a new release](https://github.com/lightbend/akka-platform-jvm-sdk/releases/new) with the appropriate tag.
    - Review the generated notes and "Publish release"
    - CI will automatically publish to the repository based on the tag
    - CI will update the docs/kalix-current branch

### Update to the latest version
 
- [ ] Review and merge PR created by bot (should appear [here](https://github.com/lightbend/akka-platform-jvm-sdk/pulls?q=is%3Apr+is%3Aopen+auto+pr+)). While reviewing confirm the release version is updated for:
    - `version` in the `samples/*/pom.xml` files
    - `akka-platform-sdk.version` in the `maven-java/akka-platform-parent/pom.xml`
    - `version` in all `maven-java/**/pom.xml`
 
### Announcements

- [ ] Add a summary of relevant changes and a link to the release notes into [Kalix Release Notes aggregation](https://docs.google.com/document/d/1Q0yWZssJHhF9oOKMW1yHq-QCyXJ-Ej8DeNuim4_QN6w/edit?usp=sharing)
- [ ] Close this issue
