#!/usr/bin/env bash
#
# Bundle a sample project for download.
#
# Usage:
#   bundle.sh --zip <file> <dir>

set -euo pipefail

function _script_path {
  local source="${BASH_SOURCE[0]}"
  while [ -h "$source" ] ; do
    local linked="$(readlink "$source")"
    local dir="$(cd -P $(dirname "$source") && cd -P $(dirname "$linked") && pwd)"
    source="$dir/$(basename "$linked")"
  done
  echo ${source}
}

readonly script_path=$(_script_path)
readonly script_dir="$(cd -P "$(dirname "$script_path")" && pwd)"
readonly docs_dir="$(cd "$script_dir/.." && pwd)"
readonly bundle_dir="$docs_dir/build/bundle"

readonly sdk_version="$("$script_dir/version.sh")"

function _remove_doc_tags {
  local -r dir="$1"
  # note: use commands that are compatible with both GNU sed and BSD (macOS) sed
  find "$dir/src" -type f -exec sed -i.bak "/tag::[^\[]*\[.*\]/d" {} \; -exec rm -f {}.bak \;
  find "$dir/src" -type f -exec sed -i.bak "/end::[^\[]*\[.*\]/d" {} \; -exec rm -f {}.bak \;
}

function _set_sdk_version {
  local -r dir="$1"
  export SDK_VERSION=$sdk_version
  # we only want to update the first occurrence of <version>, the one belonging the parent-pom
  cat $dir/pom.xml | grep 'version'
  awk '/<version>[^<]*<\/version>/ && !subyet {sub("<version>[^<]*<\/version>", "<version>"ENVIRON["SDK_VERSION"]"</version>"); subyet=1} 1' $dir/pom.xml > temp && mv temp $dir/pom.xml
  echo "Updated SDK_VERSION in $dir/pom.xml to $SDK_VERSION"
  cat $dir/pom.xml | grep 'version'
}

function _bundle {
  local zip
  local sample
  while [[ $# -gt 0 ]] ; do
    case "$1" in
      --zip | -z ) zip="$2" ; shift 2 ;;
      * ) sample=$1 ; shift ;;
    esac
  done

  [ -z "$zip" ] && echo "missing required argument for zip file" && exit 1
  [ -z "$sample" ] && echo "missing required argument for sample directory" && exit 1

  mkdir -p "$bundle_dir"
  mkdir -p "$(dirname $zip)"

  local -r sample_name="$(basename "$sample")"
  local -r sample_bundle_dir="$bundle_dir/$sample_name"
  local -r zip_dir="$(cd -P "$(dirname "$zip")" && pwd)"
  local -r zip_file="$zip_dir/$(basename "$zip")"

  rsync -a --exclude-from "$sample/.bundleignore" --exclude ".bundleignore" "$sample"/ "$sample_bundle_dir"/

  _remove_doc_tags "$sample_bundle_dir"
  _set_sdk_version "$sample_bundle_dir"

  pushd "$sample_bundle_dir" > /dev/null
  zip -q -r "$zip_file" .
  popd > /dev/null

  echo "Bundled $sample as $zip"
}

_bundle "$@"
