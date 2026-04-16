# Release Akka SDK $VERSION$

### Prepare

- [ ] Make sure all important PRs have been merged
- [ ] Check that the [latest build](https://github.com/akka/akka-sdk/actions?query=branch%3Amain) successfully finished
- [ ] Make sure a version of the Akka Runtime that supports the protocol version the SDK expects has been deployed to production. You can check this on `Dependencies.scala`

You can see the Akka Runtime version on prod [on grafana](https://app.groundcover.com/grafana/d/Kalix-Metrics/prod-kalix-metrics?orgId=6086&from=now-1h&to=now).

### Cutting the release 

- [ ] Update the license version and change date, merge the PR created by:
    ```
    bin/update-license-and-pr.sh $VERSION$
    ```
- [ ] [Draft](https://github.com/akka/akka-sdk/releases/new?tag=v$VERSION$) a new release with the tag version `v$VERSION$`. Use the **Generate release notes** button and finally press **Publish release**.
    - CI will automatically publish to the repository based on the tag
    - CI will update the docs/kalix-current branch

### Update to the latest version
 
- [ ] Review and merge PR created by bot (should appear [here](https://github.com/akka/akka-sdk/pulls?q=is%3Apr+is%3Aopen+auto+pr+)). While reviewing confirm the release version is updated for:
    - SDK `version` in the `samples/*/pom.xml` files

### Publish latest docs
- [ ] Add a summary of relevant changes into `docs/src/modules/reference/pages/release-notes.adoc`
- [ ] Create a PR and merge (do not squash) `main` into `docs-current`.
    Note that the PR will be pretty big normally, not only involving documentation files.
    ```
    gh pr create --base docs-current --head main --title "Publish docs for $VERSION$" --body "MERGE, DON'T SQUASH"
    ```

### Update samples
- [ ] Run https://github.com/akka/akka-sdk/actions/workflows/pr-akka-samples.yml from the release tag `v$VERSION$`
- [ ] Merge auto-PRs in akka-samples https://github.com/orgs/akka-samples/repositories?q=sort%3Aname-asc
  - Easiest is to use `.github/akka-sample-sdk-bump-prs.sh` script to collect all PR links or do it manually from "Open PR with changes" in https://github.com/akka/akka-sdk/actions/workflows/pr-akka-samples.yml
- [ ] Bump runtime SDK projects 
 
### Announcements

- [ ] Add a summary of relevant changes and a link to the release notes into [Akka Release Notes aggregation](https://docs.google.com/document/d/1Q0yWZssJHhF9oOKMW1yHq-QCyXJ-Ej8DeNuim4_QN6w/edit?usp=sharing)
- [ ] Close this issue
