#!/usr/bin/env bash

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
reference_dir="$script_dir/../src/modules/reference"

check_cli_nav() {
  local cli_dir="$1"
  local nav_file="$2"
  local group
  local page
  local missing=0

  if [ ! -d "$cli_dir" ] || [ ! -f "$nav_file" ]; then
    echo "CLI pages directory or reference nav file is missing" >&2
    return 1
  fi

  for page in "$cli_dir"/akka_*.adoc; do
    [ -f "$page" ] || continue
    group=${page##*/}
    [[ "$group" =~ ^akka_[a-z-]+\.adoc$ ]] || continue
    if ! grep -Eq "^\*\*\*\* xref:cli/akka-cli/${group%.adoc}\.adoc\[" "$nav_file"; then
      echo "Missing CLI group in reference nav: **** xref:cli/akka-cli/$group[]" >&2
      missing=1
    fi
  done

  while IFS= read -r group; do
    if [ ! -f "$cli_dir/$group" ]; then
      echo "Reference nav lists a CLI group that was not generated: $group" >&2
      missing=1
    fi
  done < <(sed -nE 's@^\*\*\*\* xref:cli/akka-cli/(akka_[a-z-]+\.adoc)\[.*@\1@p' "$nav_file")

  if [ "$missing" -eq 0 ]; then
    echo "Every generated CLI command group is listed in the reference nav."
  fi
  return "$missing"
}

if [ "${1:-}" = "check-nav" ]; then
  check_cli_nav "${2:-$reference_dir/pages/cli/akka-cli}" "${3:-$reference_dir/nav.adoc}"
  exit $?
fi

if command -v kramdoc > /dev/null
then
  echo kramdoc installed !
else
  echo kramdoc does not exist
  echo Please follow instruction page to install it.
  echo [Instruction Page] https://github.com/asciidoctor/kramdown-asciidoc
  exit 1
fi

if command -v akka > /dev/null
then
  echo akka installed !
else
  echo akka does not exist
  echo Please follow instruction page to install it.
  echo [Instruction Page] https://doc.akka.io/operations/cli/index.html
  exit 1
fi

echo "delete existing docs..."
cd "$reference_dir/pages/cli/akka-cli" || exit 1
rm -f akka*.adoc ./*.md ./*.md.adoc

echo "generate docs..."
# generate CLI tool markdown(md) files
akka generate-markdown-docs .

echo "convert docs to asciidoc format..."
# Each page converts independently; run up to eight kramdoc processes at once.
workers=$(getconf NPROCESSORS_ONLN 2>/dev/null || echo 4)
if [ "$workers" -gt 8 ]; then workers=8; fi
find . -maxdepth 1 -name '*.md' -type f -print0 |
  xargs -0 -n 1 -P "$workers" sh -c 'kramdoc --format=GFM --wrap=ventilate --output="$1.adoc" "$1"' _ || exit 1

# remove md files
rm *.md
rm -f index.adoc

# rename *.mod.adoc to *.adoc
for file in *.md.adoc; do mv "$file" "${file%.md.adoc}.adoc"; done

# use `./scripts/generate_cli_docs.sh gen-index` to re-generate
# the raw command index page
if [ "$1" = "gen-index" ]; then
  # extract links to a temp file
  grep "^* xref" index.adoc > temp-cli-index.txt

  # generate cli-index.adoc header
  cat > cli-index.adoc <<- EOM
////
index.adoc is auto-generated from
- header template file "cli-index-header.template.txt"
- all akka*.adoc files

Please DON'T modify file index.adoc directly.
Instead, you might want to modify file "cli-index-header.template.txt"
////
EOM
  cat cli-index-header.template.txt >> cli-index.adoc

  echo "patch command links in cli-index.adoc ..."
  AKKA_AUTH_CMD=`grep "akka_auth_" temp-cli-index.txt`
  AKKA_CODE_CMD=`grep "akka_code_" temp-cli-index.txt`
  AKKA_CONFIG_CMD=`grep "akka_config_" temp-cli-index.txt`
  AKKA_CONTAINER_REGISTRY_CMD=`grep "akka_container-registry_" temp-cli-index.txt`
  AKKA_DOCKER_CMD=`grep "akka_docker_" temp-cli-index.txt`
  AKKA_LOCAL_CMD=`grep "akka_local_" temp-cli-index.txt`
  AKKA_ORGANIZATIONS_CMD=`grep "akka_organizations_" temp-cli-index.txt`
  AKKA_PROJECTS_CMD=`grep "akka_projects_" temp-cli-index.txt`
  AKKA_REGIONS_CMD=`grep "akka_regions_" temp-cli-index.txt`
  AKKA_ROLES_CMD=`grep "akka_roles_" temp-cli-index.txt`
  AKKA_ROUTES_CMD=`grep "akka_routes_" temp-cli-index.txt`
  AKKA_SECRETS_CMD=`grep "akka_secrets_" temp-cli-index.txt`
  AKKA_SERVICES_COMPONENTS_CMD=`grep "akka_services_components_" temp-cli-index.txt`
  AKKA_SERVICE_CONFIGS_CMD=`grep "akka_service-configs_" temp-cli-index.txt`
  AKKA_SERVICES_CMD=`grep "akka_services_" temp-cli-index.txt`
  AKKA_SPECIFY_CMD=`grep "akka_specify_" temp-cli-index.txt`

  perl -pi.bak -e "s|<AKKA_AUTH_CMD>|${AKKA_AUTH_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_SERVICES_COMPONENTS_CMD>|${AKKA_SERVICES_COMPONENTS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_CONFIG_CMD>|${AKKA_CONFIG_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_CODE_CMD>|${AKKA_CODE_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_CONTAINER_REGISTRY_CMD>|${AKKA_CONTAINER_REGISTRY_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_DOCKER_CMD>|${AKKA_DOCKER_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_LOCAL_CMD>|${AKKA_LOCAL_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_ORGANIZATIONS_CMD>|${AKKA_ORGANIZATIONS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_PROJECTS_CMD>|${AKKA_PROJECTS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_REGIONS_CMD>|${AKKA_REGIONS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_ROLES_CMD>|${AKKA_ROLES_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_ROUTES_CMD>|${AKKA_ROUTES_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_SECRETS_CMD>|${AKKA_SECRETS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_SERVICE_CONFIGS_CMD>|${AKKA_SERVICE_CONFIGS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_SERVICES_CMD>|${AKKA_SERVICES_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_SPECIFY_CMD>|${AKKA_SPECIFY_CMD}|" cli-index.adoc

  echo "patch adoc reference path in cli-index.adoc ..."
  # replace links from "* xref:" to "* xref:reference:akka/" and write it to index.adoc
  awk '{gsub(/^\* xref:/,"* xref:reference:cli/akka-cli/"); print}' cli-index.adoc > index.adoc
  rm temp-cli-index.txt cli-index.adoc cli-index.adoc.bak

fi

# change header level
for file in akka*.adoc; do
  sed -e 's/^==/=/' $file > $file.tmp
  mv $file.tmp $file
done

# inject page-aliases so renamed commands keep their old (bookmarked) URLs alive.
# Mapping lives in cli-redirects.txt (survives the `rm -f akka*.adoc` above).
# See that file for the format.
if [ -f cli-redirects.txt ]; then
  echo "inject page-aliases redirects..."
  while IFS= read -r line; do
    # skip blank lines and comments
    case "$line" in
      ''|\#*) continue ;;
    esac
    new_page=$(echo "$line" | cut -d: -f1 | tr -d '[:space:]')
    old_pages=$(echo "$line" | cut -d: -f2 | tr -d '[:space:]')
    target="${new_page}.adoc"
    if [ ! -f "$target" ]; then
      echo "  WARNING: redirect target $target not found, skipping"
      continue
    fi
    # turn "old1,old2" into a :page-aliases: value "old1.adoc, old2.adoc"
    aliases=$(echo "$old_pages" | awk -F, '{for(i=1;i<=NF;i++){printf "%s.adoc%s", $i, (i<NF?", ":"")}}')
    # insert the attribute right after the first title line (= ...)
    awk -v al="$aliases" '
      ins==0 && /^= / { print; print ":page-aliases: " al; ins=1; next }
      { print }
    ' "$target" > "$target.tmp" && mv "$target.tmp" "$target"
    echo "  $target -> $aliases"
  done < cli-redirects.txt
fi

# remove tooling footer
find . -type f -name '*.adoc' -exec sed -i -e '$s/^===== Auto generated by .*//' {} \;

check_cli_nav "$reference_dir/pages/cli/akka-cli" "$reference_dir/nav.adoc" || true
echo "done"
