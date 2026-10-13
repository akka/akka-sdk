# Make Akka SDK for Java documentation
SHELL_DIR := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ROOT_DIR := ${SHELL_DIR}
TARGET_DIR := ${ROOT_DIR}/target/site
NEXUS_DIR ?= ${ROOT_DIR}/../nexus
AKKA_CLI_VERSION := 3.0.77

upstream := akka/akka-sdk
branch   := docs/current
sources  := src build/src/managed

src_managed := docs/src-managed

java_managed_attachments := ${src_managed}/modules/sdk/attachments
java_managed_examples := ${src_managed}/modules/sdk/examples
managed_partials := ${src_managed}/modules/ROOT/partials
# Optimize CLI downloads use the release tags from its own repository.
optimize_version = $(shell git -C "${NEXUS_DIR}" describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null | sed 's/^v//')

antora_docker_image := local/antora-doc
antora_docker_image_tag := latest
BASE_PATH := $(shell git rev-parse --show-prefix)

antora_worktree_repo := ${ROOT_DIR}/target/antora-content-source

# Sets the docker mounts for an Antora build.
# Antora requires a content source whose .git is a directory. A linked git worktree is
# mounted through a scratch repository.
define antora_mounts
	mounts="-v ${ROOT_DIR}:/antora"; \
	if [ -f "${ROOT_DIR}/.git" ]; then \
		mkdir -p "${ROOT_DIR}/target"; \
		docs/bin/antora-worktree-repo.sh "${antora_worktree_repo}"; \
		mounts="-v ${antora_worktree_repo}:/antora -v ${ROOT_DIR}/docs:/antora/docs -v ${ROOT_DIR}/target:/antora/target"; \
	fi;
endef

.SILENT:
.PHONY: check-optimize markdown whitepapers whitepapers-ci

build: managed local open

clean:
	rm -rf "${src_managed}"
	rm -rf target/site

docker-image:
	(cd ${ROOT_DIR}/docs/antora-docker;  docker build -t ${antora_docker_image}:${antora_docker_image_tag} .)

prepare:
	mkdir -p "${src_managed}"
	cp docs/src/antora.yml "${src_managed}"
	mkdir -p "${java_managed_attachments}"
	cp akka-javasdk-testkit/src/main/resources/akka/javasdk/testkit/eval/eval-report.example.json "${java_managed_attachments}/"

managed: check-optimize prepare apidocs attributes examples bundles

attributes: apidocs
	test -s target/docs-runtime-version.txt
	mkdir -p "${managed_partials}"
	echo "// generated from Makefile" \
		> "${managed_partials}/attributes.adoc"
	docs/bin/version.sh | xargs -0  printf ":akka-javasdk-version: %s" \
		> "${managed_partials}/attributes.adoc"
	echo ":akka-runtime-version: $$(cat target/docs-runtime-version.txt)" \
		>> "${managed_partials}/attributes.adoc"
	echo ":akka-cli-version: ${AKKA_CLI_VERSION}" >> "${managed_partials}/attributes.adoc"
	echo ":akka-cli-min-version: 3.0.4" >> "${managed_partials}/attributes.adoc"
	# see https://adoptium.net/marketplace/
	echo ":java-version: 25" \
		>> "${managed_partials}/attributes.adoc"
	# see https://maven.apache.org/docs/history.html
	echo ":minimum_maven_version: 3.9" \
		>> "${managed_partials}/attributes.adoc"
	# see https://docs.docker.com/engine/release-notes/27/
	echo ":minimum_docker_version: 27" \
		>> "${managed_partials}/attributes.adoc"

apidocs: prepare
	mkdir -p "${java_managed_attachments}"
	sbt docsApi
	rsync -a akka-javasdk/target/api/ "${java_managed_attachments}/api/"
	rsync -a akka-javasdk-testkit/target/api/ "${java_managed_attachments}/testkit/"
	docs/bin/version.sh > "${java_managed_attachments}/latest-version.txt"
	# also keep version in previous location for the Runtime version check (Runtimes < 1.5.21)
	mkdir -p "${src_managed}/modules/java/attachments"
	docs/bin/version.sh > "${src_managed}/modules/java/attachments/latest-version.txt"

examples: prepare
	mkdir -p "${java_managed_examples}"
	rsync -a --exclude-from=docs/.examplesignore samples/* "${java_managed_examples}/"
	mkdir -p "${java_managed_examples}/akka-javasdk/src/main/"
	rsync -a akka-javasdk/src/main/resources "${java_managed_examples}/akka-javasdk/src/main/"
	mkdir -p "${java_managed_examples}/akka-javasdk/src/main/java/akka/javasdk/agent/"
	rsync -a akka-javasdk/src/main/java/akka/javasdk/agent/evaluator "${java_managed_examples}/akka-javasdk/src/main/java/akka/javasdk/agent/"
	mkdir -p "${java_managed_examples}/akka-javasdk-testkit/src/main/java/akka/javasdk/testkit/"
	rsync -a akka-javasdk-testkit/src/main/java/akka/javasdk/testkit/eval "${java_managed_examples}/akka-javasdk-testkit/src/main/java/akka/javasdk/testkit/"
	# Remove prettier-ignore comments from copied examples
	docs/bin/remove-prettier-ignore.sh "${java_managed_examples}"

bundles:
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/shopping-cart-quickstart.zip" samples/shopping-cart-quickstart
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/customer-registry-quickstart.zip" samples/event-sourced-customer-registry
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/choreography-saga-quickstart.zip" samples/choreography-saga-quickstart
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/workflow-quickstart.zip" samples/transfer-workflow-compensation

# CI must install browser system dependencies and fail when Node.js is unavailable.
whitepapers-ci: WHITEPAPERS_REQUIRED := 1
whitepapers-ci: WHITEPAPERS_INSTALL := 1
whitepapers-ci: PLAYWRIGHT_INSTALL_FLAGS := --with-deps
whitepapers-ci: whitepapers

whitepapers:
	if ! command -v node >/dev/null 2>&1; then \
	  if [ "$(WHITEPAPERS_REQUIRED)" = 1 ]; then \
	    echo "Node.js is required to render white paper PDFs." >&2; exit 1; \
	  fi; \
	  echo ">> Skipping white paper PDF: Node.js not found (install Node to render it locally)."; \
	else \
	  if [ "$(WHITEPAPERS_INSTALL)" = 1 ] || [ ! -d docs/bin/whitepaper/node_modules ]; then \
	    echo ">> Installing white paper render tooling (Playwright + Chromium)..."; \
	    (cd docs/bin/whitepaper && npm install && npx playwright install $(PLAYWRIGHT_INSTALL_FLAGS) chromium --only-shell) || exit 1; \
	  fi; \
	  node docs/bin/whitepaper/render-pdf.mjs "${TARGET_DIR}"; \
	fi

markdown:
	test -d "${TARGET_DIR}"
	npm ci --prefix docs/bin/markdown
	./docs/bin/docs2markdown.sh

done:
	@echo "Generated docs at ${TARGET_DIR}/index.html"

open:
	open "${TARGET_DIR}/index.html"

local: check-optimize docker-image examples optimize-content antora-local whitepapers done

prod: check-optimize docker-image managed optimize-content antora-prod done

check-optimize:
	if [ ! -e "${NEXUS_DIR}/.git" ] || [ ! -f "${NEXUS_DIR}/optimize-docs/src/antora.yml" ]; then \
		echo "Optimize documentation checkout is missing at ${NEXUS_DIR}." >&2; \
		echo "Clone it beside akka-sdk: git clone git@github.com:akka/nexus.git ../nexus" >&2; \
		echo "Or set NEXUS_DIR to the absolute path of an existing nexus checkout." >&2; \
		exit 1; \
	fi

antora-local:
	test -n "$(optimize_version)" || { echo "No Optimize release tag found in ${NEXUS_DIR}; fetch its tags before building the site." >&2; exit 1; }
	$(antora_mounts) \
	docker run \
		--user "$$(id -u):$$(id -g)" \
		$$mounts \
		-v "${NEXUS_DIR}:/optimize:ro" \
		--rm \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		--cache-dir=.cache/antora --stacktrace --log-failure-level=warn \
		--attribute "optimize-version=$(optimize_version)" \
		docs/antora-playbook-local.yml

optimize-content: check-optimize
	docs/bin/prepare-optimize-docs.sh "${NEXUS_DIR}"

antora-prod:
	test -n "$(optimize_version)" || { echo "No Optimize release tag found in ${NEXUS_DIR}; fetch its tags before building the site." >&2; exit 1; }
	$(antora_mounts) \
	docker run \
		--user "$$(id -u):$$(id -g)" \
		$$mounts \
		-v "${NEXUS_DIR}:/optimize:ro" \
		--rm \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		--cache-dir=.cache/antora --stacktrace --log-level error --log-failure-level=warn \
		--attribute "optimize-version=$(optimize_version)" \
		docs/antora-playbook-prod.yml

validate-links:
	docker run \
		-v ${ROOT_DIR}:/antora \
		--rm \
		--entrypoint /bin/sh \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c "cd /antora/${BASE_PATH} && find src -name '*.adoc' -print0 | xargs -0 -n1 asciidoc-link-check --progress --config config/validate-links.json"

verify-internal-refs:
	ruby docs/bin/verify-internal-refs.rb

vale:
	docs/bin/vale.sh

deploy: clean managed
	bin/deploy.sh --module java --upstream ${upstream} --branch ${branch} ${sources}
