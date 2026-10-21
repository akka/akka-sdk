# Release metadata

`versions.json` stores stable SDK, Runtime, and CLI release histories, ordered by
numeric version with the newest first. Each component has a `releases` array,
initially empty. Entries are retained without automatic pruning. Future platform data is preserved.

```json
{
  "schemaVersion": 1,
  "sdk": {
    "releases": [
      {
        "version": "3.7.0",
        "releasedAt": "2026-10-16T12:00:46Z",
        "notifyAt": "2026-10-26T12:00:46Z",
        "releaseNotesUrl": "https://doc.akka.io/reference/release-notes.html#sdk-3-7-0",
        "requiredRuntime": "1.7.2",
        "protocolVersion": "1.1"
      }
    ]
  },
  "runtime": {
    "releases": [
      {
        "version": "1.7.2",
        "releasedAt": "2026-10-12T14:08:09Z",
        "releaseNotesUrl": "https://doc.akka.io/reference/release-notes/runtime/1.7.2.html",
        "supportedProtocol": { "minimum": "1.0", "maximum": "1.2" }
      }
    ]
  },
  "cli": {
    "releases": [
      {
        "version": "3.0.78",
        "releasedAt": "2026-10-15T12:00:00Z",
        "releaseNotesUrl": "https://doc.akka.io/reference/release-notes.html#cli-3-0-78"
      }
    ]
  }
}
```

These values and SDK/CLI documentation anchors are illustrative.

## SDK notification timing

Only SDK releases have `notifyAt`. The updater calculates it as `releasedAt` plus
`notify_delay_days`, which defaults to 10. If the required Runtime version matches
the preceding SDK release's requirement, the updater automatically uses zero delay.
The first SDK release and releases with a changed requirement use the configured
delay. Out-of-order submissions are compared with the preceding SDK version, not
the newest catalog entry. Zero delay is represented by `notifyAt` equal to
`releasedAt`, rather than the number `0`. You can also set the delay to zero for
immediate eligibility when the requirement changes.
Days are whole 24-hour periods in UTC; negative or fractional values
are rejected. The delay input is ignored for Runtime and CLI submissions, whose
entries have no notification timing fields.

A client selects the newest SDK release whose `notifyAt` is at or before the
current time and whose version is newer than the version in use. Keeping earlier
releases allows a notification to become eligible even when SDK releases are more
frequent than the delay. The static JSON does not need scheduled updates for this
rule to work. Compatibility and regional availability are separate considerations.

Resubmit the same version with another delay to move `notifyAt`, or to correct
release dates, compatibility data, and documentation links. A same-version
submission replaces that entry. It does not add a duplicate. The unchanged-Runtime
rule also applies on resubmission. Otherwise, resubmitting without an explicit delay
restores the default 10-day delay, so preserve an urgent release's zero-day override
when making other corrections.

## Submitting a release

The SDK **Publish** workflow submits metadata after successful artifact
publication and `publishM2`. Its shell script reads the GitHub Release date and
the expected Runtime/protocol values from the generated parent POM installed by
that build. Stable tags dispatch **Update release metadata** on `main` using
`GITHUB_TOKEN` with `actions: write`; snapshots, prereleases, and drafts are skipped.
The dispatch uses the default 10-day SDK delay and leaves `releaseNotesUrl` empty
unless `RELEASE_NOTES_URL` is explicitly supplied. A missing published GitHub Release
or failed dispatch fails the publishing run without retries. Resubmit metadata
separately if artifacts have already been published; do not rerun artifact
publication to recover a metadata failure.

Invoke **Update release metadata** on `main` with a credential authorized to
dispatch workflows in `akka/akka-sdk`. Each producer supplies selected public
metadata from its own GitHub Release and tagged source; the receiver needs no
internal repository access.

```sh
gh workflow run update-release-metadata.yml --repo akka/akka-sdk --ref main \
  -f component=sdk -f version="$SDK_VERSION" \
  -f released_at="$RELEASE_PUBLISHED_AT" -f notify_delay_days=10 \
  -f release_notes_url="$PUBLIC_RELEASE_NOTES_URL" \
  -f required_runtime="$EXPECTED_RUNTIME" -f protocol_version="$SDK_PROTOCOL"
```

Use GitHub Release `published_at` in UTC and the SDK's existing expected Runtime
requirement. Runtime submissions require `minimum_protocol` and `maximum_protocol`;
CLI submissions have no compatibility fields. The release-notes URL is optional;
omit `release_notes_url` to store `releaseNotesUrl: ""` until a designated URL is
available. Resubmit the same version to add it later. Blank resubmissions preserve
an existing designated URL. Non-empty URLs must use `https://doc.akka.io/` but need
not exist yet. An intended SDK version anchor can be supplied and corrected later.
No HTTP availability check blocks submission. Pass `prerelease=true` for a GitHub
prerelease; that flag, version qualifiers, and snapshots cause the submission to
be ignored.

Three- and four-part numeric versions are supported. Older versions can be added
as history. Identical submissions leave the file unchanged. Other components are preserved. Unsupported schemas, duplicate
versions, malformed entries, and unsorted lists fail validation without changing
the catalog.

Each submission opens a metadata PR on a branch unique to the workflow run and
attempt. Review and merge it; resolve catalog conflicts if several PRs are open.
Git and GitHub operations are attempted once. Successful dispatch does not establish
that the PR was created: check the receiving workflow run.

## Publication

After merge, **Publish release metadata** checks out current `main`, validates the
catalog using the same Node module as the updater, and uploads only `versions.json`
to `https://doc.akka.io/versions.json`. Manual publication from `main` is supported.
Uploads are serialized, with no retries or artifact/documentation builds.

The SDK and CLI's existing text version endpoints and their publication remain
unchanged. Delayed SDK notifications only apply to clients that read `versions.json`
and use `notifyAt`; existing Runtimes reading `latest-version.txt` retain their
current notification behavior.

## Local checks

Use Node 24, as configured in both workflows. No npm packages, jq, or GNU sort
are needed for metadata logic; Git, gh, and rsync orchestration remains in shell.
The SDK release submission script uses jq to read the GitHub API response, so its
mocked integration test also requires jq and Bash.

```sh
node --test .github/scripts/release-metadata.test.mjs
node .github/scripts/update-release-metadata.mjs --validate release-metadata/versions.json
```

To test updates, copy the checked-in catalog and set the workflow fields as
uppercase environment variables (`PRERELEASE` defaults to false and
`NOTIFY_DELAY_DAYS` to 10 for SDK releases):

```sh
cp release-metadata/versions.json /tmp/versions.json
node .github/scripts/update-release-metadata.mjs /tmp/versions.json
```

The updater requires an existing catalog and writes through a temporary file and
rename. The commit script is restricted to a clean, disposable Actions checkout.
Release publication does not establish artifact readiness or regional availability.
