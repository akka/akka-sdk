# Make Akka SDK for Java documentation
SHELL_DIR := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ROOT_DIR := ${SHELL_DIR}
TARGET_DIR := ${ROOT_DIR}/target/site

upstream := akka/akka-sdk
branch   := docs/current
sources  := src build/src/managed

src_managed := docs/src-managed

java_managed_attachments := ${src_managed}/modules/java/attachments
java_managed_examples := ${src_managed}/modules/java/examples
managed_partials := ${src_managed}/modules/ROOT/partials

antora_docker_image := local/antora-doc
antora_docker_image_tag := latest
BASE_PATH := $(shell git rev-parse --show-prefix)

.SILENT:

build: managed local open

clean:
	rm -rf "docs/src/modules/java/attachments"
	rm -rf "docs/src/modules/java/examples"
	rm -rf "docs/src/modules/ROOT/partials/attributes.adoc"
	# above can eventually be removed, left for a smooth transition to managed files
	rm -rf "${src_managed}"
	rm -rf target/site

docker-image:
	(cd ${ROOT_DIR}/docs/antora-docker;  docker build -t ${antora_docker_image}:${antora_docker_image_tag} .)

prepare:
	mkdir -p "${src_managed}"
	cp docs/src/antora.yml "${src_managed}"

managed: prepare attributes apidocs examples bundles

attributes: prepare
	mkdir -p "${managed_partials}"
	echo "// generated from Makefile" \
		> "${managed_partials}/attributes.adoc"
	docs/bin/version.sh | xargs -0  printf ":akka-javasdk-version: %s" \
		> "${managed_partials}/attributes.adoc"
	echo ":akka-cli-version: 3.0.16" >> "${managed_partials}/attributes.adoc"
	echo ":akka-cli-min-version: 3.0.4" >> "${managed_partials}/attributes.adoc"
	# see https://adoptium.net/marketplace/
	echo ":java-version: 21" \
		>> "${managed_partials}/attributes.adoc"
	# see https://maven.apache.org/docs/history.html
	echo ":minimum_maven_version: 3.9" \
		>> "${managed_partials}/attributes.adoc"
	# see https://docs.docker.com/engine/release-notes/27/
	echo ":minimum_docker_version: 27" \
		>> "${managed_partials}/attributes.adoc"

apidocs: prepare
	mkdir -p "${java_managed_attachments}"
	sbt akka-javasdk/doc akka-javasdk-testkit/doc
	rsync -a akka-javasdk/target/api/ "${java_managed_attachments}/api/"
	rsync -a akka-javasdk-testkit/target/api/ "${java_managed_attachments}/testkit/"
	docs/bin/version.sh > "${java_managed_attachments}/latest-version.txt"

examples: prepare
	mkdir -p "${java_managed_examples}"
	rsync -a --exclude-from=docs/.examplesignore samples/* "${java_managed_examples}/"

bundles:
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/shopping-cart-quickstart.zip" samples/shopping-cart-quickstart
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/customer-registry-quickstart.zip" samples/event-sourced-customer-registry
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/choreography-saga-quickstart.zip" samples/choreography-saga-quickstart
	./docs/bin/bundle.sh --zip "${java_managed_attachments}/workflow-quickstart.zip" samples/transfer-workflow-compensation

done:
	@echo "Generated docs at ${TARGET_DIR}/index.html"

open:
	open "${TARGET_DIR}/index.html"

local: docker-image examples antora-local done

prod: docker-image managed antora-prod done

antora-local:
	docker run \
		-v ${ROOT_DIR}:/antora \
		--rm \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		--cache-dir=.cache/antora --stacktrace --log-failure-level=warn \
		docs/antora-playbook-local.yml

antora-prod:
	docker run \
		-v ${ROOT_DIR}:/antora \
		--rm \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		--cache-dir=.cache/antora --stacktrace --log-level error --log-failure-level=warn \
		docs/antora-playbook-prod.yml

validate-links:
	docker run \
		-v ${ROOT_DIR}:/antora \
		--rm \
		--entrypoint /bin/sh \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c "cd /antora/${BASE_PATH} && find src -name '*.adoc' -print0 | xargs -0 -n1 asciidoc-link-check --progress --config config/validate-links.json"

deploy: clean managed
	bin/deploy.sh --module java --upstream ${upstream} --branch ${branch} ${sources}
