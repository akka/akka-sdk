#!/usr/bin/env bash
set -euo pipefail

optimize_dir="${1:?Optimize checkout directory is required}"
docs_dir="${optimize_dir}/optimize-docs"

make -C "${docs_dir}" examples bundles

# The release notes in Optimize 0.10.1 show API paths with {id} style segments as attribute
# references, and the warnings fail the build. Remove this after the next Optimize bump.
perl -pi -e 's/`((?:POST|GET) [^`+]*\{(?:id|ns|name|vN|analysisId)\}[^`]*)`/`+$1+`/g' \
  "${docs_dir}"/src/modules/optimize/pages/release-notes/*.adoc

# The SDK component descriptor registers the navigation for both repositories.
source_descriptor="${docs_dir}/src/antora.yml"
managed_descriptor="${docs_dir}/src-managed/antora.yml"
descriptor_tmp="$(mktemp "${source_descriptor}.XXXXXX")"
trap 'rm -f "${descriptor_tmp}"' EXIT
awk '
  /^name:/ { name = $0 }
  /^version:/ { version = $0 }
  END {
    if (name == "" || version == "") exit 1
    print name
    print version
  }
' "${source_descriptor}" > "${descriptor_tmp}"
mv "${descriptor_tmp}" "${source_descriptor}"
cp "${source_descriptor}" "${managed_descriptor}"
