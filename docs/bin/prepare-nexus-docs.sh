#!/usr/bin/env bash
set -euo pipefail

nexus_dir="${1:?Nexus checkout directory is required}"
docs_dir="${nexus_dir}/optimize-docs"

make -C "${docs_dir}" examples bundles

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
