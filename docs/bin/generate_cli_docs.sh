#!/usr/bin/env bash

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
  echo [Instruction Page] https://doc.akka.io/operating/cli/installation.html
  exit 1
fi

echo "delete existing docs..."
cd docs/src/modules/reference/pages/cli/ || exit 1
rm -f akka*.adoc

echo "generate docs..."
# generate CLI tool markdown(md) files
akka generate-markdown-docs .

echo "convert docs to asciidoc format..."
# convert md files to adoc format (NOTE: the extension is changed to *.mod.adoc)
find ./ -name "*.md" -type f -exec sh -c 'kramdoc --format=GFM --wrap=ventilate --output={}.adoc {}' \;

# remove md files
rm *.md
rm index.adoc

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
- all kalix*.adoc files

Please DON'T modify file index.adoc directly.
Instead, you might want to modify file "cli-index-header.template.txt"
////
EOM
  cat cli-index-header.template.txt >> cli-index.adoc

  echo "patch command links in cli-index.adoc ..."
  AKKA_AUTH_CMD=`grep "akka_auth_" temp-cli-index.txt`
  AKKA_CONFIG_CMD=`grep "akka_config_" temp-cli-index.txt`
  AKKA_CONTAINER_REGISTRY_CMD=`grep "akka_container-registry_" temp-cli-index.txt`
  AKKA_DOCKER_CMD=`grep "akka_docker_" temp-cli-index.txt`
  AKKA_LOCAL_CMD=`grep "akka_local_" temp-cli-index.txt`
  AKKA_ORGANIZATIONS_CMD=`grep "akka_organizations_" temp-cli-index.txt`
  AKKA_PROJECTS_CMD=`grep "akka_projects_" temp-cli-index.txt`
  AKKA_QUICKSTART_CMD=`grep "akka_quickstart_" temp-cli-index.txt`
  AKKA_REGIONS_CMD=`grep "akka_regions_" temp-cli-index.txt`
  AKKA_ROLES_CMD=`grep "akka_roles_" temp-cli-index.txt`
  AKKA_ROUTES_CMD=`grep "akka_routes_" temp-cli-index.txt`
  AKKA_SECRETS_CMD=`grep "akka_secrets_" temp-cli-index.txt`
  AKKA_SERVICES_CMD=`grep "akka_services_" temp-cli-index.txt`

  perl -pi.bak -e "s|<AKKA_AUTH_CMD>|${AKKA_AUTH_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_CONFIG_CMD>|${AKKA_CONFIG_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_CONTAINER_REGISTRY_CMD>|${AKKA_CONTAINER_REGISTRY_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_DOCKER_CMD>|${AKKA_DOCKER_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_LOCAL_CMD>|${AKKA_LOCAL_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_ORGANIZATIONS_CMD>|${AKKA_ORGANIZATIONS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_PROJECTS_CMD>|${AKKA_PROJECTS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_QUICKSTART_CMD>|${AKKA_QUICKSTART_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_REGIONS_CMD>|${AKKA_REGIONS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_ROLES_CMD>|${AKKA_ROLES_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_ROUTES_CMD>|${AKKA_ROUTES_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_SECRETS_CMD>|${AKKA_SECRETS_CMD}|" cli-index.adoc
  perl -pi.bak -e "s|<AKKA_SERVICES_CMD>|${AKKA_SERVICES_CMD}|" cli-index.adoc

  echo "patch adoc reference path in cli-index.adoc ..."
  # replace links from "* xref:" to "* xref:reference:cli/" and write it to index.adoc
  awk '{gsub(/^\* xref:/,"* xref:reference:cli/"); print}' cli-index.adoc > index.adoc
  rm temp-cli-index.txt cli-index.adoc cli-index.adoc.bak

  # create nav entries to be replaced manually
  ls -1 akka*.adoc > ../../temp-nav.txt
  sed -e 's/\(akka.*\)/**** xref:cli\/\1[]/g' ../../temp-nav.txt > ../../../nav2.adoc
  rm ../../temp-nav.txt
fi

# change header level
for file in akka*.adoc; do
  sed -e 's/^==/=/' $file > $file.tmp
  mv $file.tmp $file
done

# remove tooling footer
find . -type f -name '*.adoc' -exec sed -i -e '$s/^===== Auto generated by .*//' {} \;

echo "done"
