# Release metadata

`versions.json` stores stable SDK, Runtime, and CLI release histories, ordered by
numeric version with the newest first. Each component has a `releases` array.
Entries are never pruned. Other top-level keys are preserved.

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

## SDK notification timing

Only SDK releases have `notifyAt`. The updater sets it as follows:

- An explicit delay sets `notifyAt` to `releasedAt` plus that many whole days
  (0 to 9999, UTC). A delay of zero makes the release eligible immediately.
- Without a delay, an existing version keeps its `notifyAt`.
- Without a delay, a new version copies `notifyAt` from the preceding SDK version
  when both require the same Runtime. The date is copied even if it has passed.
  A pending date stays pending, so a patch does not announce a held-back release early.
- Otherwise a new version uses a delay of 10 days.

The updater selects the preceding version by version number, not by submission
order, and never recalculates existing entries.

A client selects the newest SDK release whose `notifyAt` is at or before the
current time and whose version is newer than the version in use. Keeping earlier
releases lets a release become eligible even when releases are more frequent than
the delay. No scheduled update is needed.

## Submitting a release

The SDK **Publish** workflow submits metadata after `publishM2` succeeds on a stable
`vX.Y.Z` tag. The submit script reads the release date from the GitHub Release and
the Runtime and protocol versions from the generated parent POM. It then dispatches
**Update release metadata** on `main` without a delay. The script skips draft and
prerelease GitHub Releases. A failed dispatch fails the publishing run. Resubmit
the metadata manually instead of rerunning artifact publication.

To submit manually, dispatch **Update release metadata** on `main` with a credential
that can run workflows in `akka/akka-sdk`:

```sh
gh workflow run update-release-metadata.yml --repo akka/akka-sdk --ref main \
  -f component=sdk -f version="$SDK_VERSION" \
  -f released_at="$RELEASE_PUBLISHED_AT" -f notify_delay_days=10 \
  -f release_notes_url="$PUBLIC_RELEASE_NOTES_URL" \
  -f required_runtime="$EXPECTED_RUNTIME" -f protocol_version="$SDK_PROTOCOL"
```

- `released_at` is the GitHub Release `published_at` in UTC.
- Runtime entries also require `minimum_protocol` and `maximum_protocol`.
- CLI entries have no compatibility fields.
- `release_notes_url` must use `https://doc.akka.io/`. The page does not need to
  exist yet. A blank value keeps the stored URL, or uses
  `https://doc.akka.io/reference/release-notes.html` for a new release.
- Versions must be stable numeric versions with three parts, or four parts for the CLI.
  Qualifiers and snapshots fail validation.
- Resubmit an existing version to correct it. Omitted fields keep their values.
  Adding a URL does not change `notifyAt`. An explicit delay recalculates it.
- Unsupported schemas, duplicate versions, malformed entries, and unsorted lists
  fail validation and leave the file unchanged.

Each submission opens a pull request from a branch named for the workflow run and
attempt. Review and merge it. A successful dispatch does not mean the pull request
exists, so check the receiving workflow run.

## Publication

After merge, **Publish release metadata** validates `versions.json` with the same
module as the updater and uploads it to `https://doc.akka.io/versions.json`.
Uploads run one at a time. You can also run the workflow manually from `main`.

The SDK and CLI text version endpoints are unchanged. Only clients that read
`versions.json` use `notifyAt`. Runtimes that read `latest-version.txt` keep their
current notification behavior.

## Local checks

Use Node 24. The SDK submit script test also requires jq and Bash.

```sh
node --test .github/scripts/release-metadata.test.mjs
node .github/scripts/update-release-metadata.mjs --validate release-metadata/versions.json
```

To test an update, copy the catalog and pass the workflow inputs as uppercase
environment variables. An empty `NOTIFY_DELAY_DAYS` uses the rules above.

```sh
cp release-metadata/versions.json /tmp/versions.json
COMPONENT=cli VERSION=3.0.78 RELEASED_AT=2026-10-15T12:00:00Z \
  node .github/scripts/update-release-metadata.mjs /tmp/versions.json
```
