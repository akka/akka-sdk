# Release metadata

`versions.json` stores one current stable release per SDK, Runtime, and CLI.
Each component is a release object, without arrays, channel fields, or a
`latestStable` pointer. A component remains `null` until its first real submission;
`generatedAt` remains `null` until the first update. Future platform data is preserved.

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-10-20T12:00:00Z",
  "sdk": {
    "version": "3.7.0",
    "releasedAt": "2026-10-16T12:00:46Z",
    "releaseNotesUrl": "https://doc.akka.io/reference/release-notes.html#sdk-3-7-0",
    "requiredRuntime": "1.7.2",
    "protocolVersion": "1.1"
  },
  "runtime": {
    "version": "1.7.2",
    "releasedAt": "2026-10-12T14:08:09Z",
    "releaseNotesUrl": "https://doc.akka.io/reference/release-notes/runtime/1.7.2.html",
    "supportedProtocol": { "minimum": "1.0", "maximum": "1.2" }
  },
  "cli": {
    "version": "3.0.78",
    "releasedAt": "2026-10-15T12:00:00Z",
    "releaseNotesUrl": "https://doc.akka.io/reference/release-notes.html#cli-3-0-78"
  }
}
```

These values and SDK/CLI documentation anchors are illustrative.

## Submitting a release

Invoke **Update release metadata** on `main` with a credential authorized to
dispatch workflows in `akka/akka-sdk`. Each producer supplies selected public
metadata from its own GitHub Release and tagged source; the receiver needs no
internal repository access.

```sh
gh workflow run update-release-metadata.yml --repo akka/akka-sdk --ref main \
  -f component=sdk -f version="$SDK_VERSION" \
  -f released_at="$RELEASE_PUBLISHED_AT" \
  -f release_notes_url="$PUBLIC_RELEASE_NOTES_URL" \
  -f required_runtime="$EXPECTED_RUNTIME" -f protocol_version="$SDK_PROTOCOL"
```

## Publication

After merge, **Publish release metadata** checks out current `main`, validates the
catalog using the same Node module as the updater, and uploads only `versions.json`
to `https://doc.akka.io/versions.json`. Manual publication from `main` is supported.
Uploads are serialized, with no retries or artifact/documentation builds.

## Local checks

Use Node 24, as configured in both workflows. No npm packages, jq, or GNU sort
are needed for metadata logic; Git, gh, and rsync orchestration remains in shell.

```sh
node --test .github/scripts/release-metadata.test.mjs
node .github/scripts/update-release-metadata.mjs --validate release-metadata/versions.json
```

To test updates, copy the checked-in catalog and set the workflow fields as
uppercase environment variables (`PRERELEASE` defaults to false):

```sh
cp release-metadata/versions.json /tmp/versions.json
node .github/scripts/update-release-metadata.mjs /tmp/versions.json
```

The updater requires an existing catalog and writes through a temporary file and
rename. The commit script is restricted to a clean, disposable Actions checkout.
Release publication does not establish artifact readiness or regional availability.
