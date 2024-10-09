# Make Akka SDK for Java documentation
SHELL_DIR := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ROOT_DIR := ${SHELL_DIR}
TARGET_DIR := ${ROOT_DIR}/target/site

upstream := lightbend/akka-javasdk
branch   := docs/current
sources  := src build/src/managed

src_managed := docs/src-managed

java_managed_attachments := docs/src/modules/java/attachments
java_managed_examples := docs/src/modules/java/examples
java_managed_partials := docs/src/modules/java/partials

antora_docker_image := local/antora-doc
antora_docker_image_tag := latest
BASE_PATH := $(shell git rev-parse --show-prefix)

.SILENT:

build: dev

clean:
	rm -rf "${java_managed_attachments}"
	rm -rf "${java_managed_examples}"
	rm -rf "${java_managed_partials}/attributes.adoc"
	rm -rf target/site

docker-image:
	(cd ${ROOT_DIR}/docs/antora-docker;  docker build -t ${antora_docker_image}:${antora_docker_image_tag} .)

prepare:
	mkdir -p "${src_managed}"
	#cp docs/docs-managed-antora.yml "${src_managed}/antora.yml"

managed: prepare attributes apidocs examples bundles

attributes: prepare
	mkdir -p "${java_managed_partials}"
	docs/bin/version.sh | xargs -0  printf ":akka-javasdk-version: %s" \
		> "${java_managed_partials}/attributes.adoc"
	echo ":java-pb-version: 11" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":java-version: 21" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":minimum_maven_version: 3.6" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":minimum_sbt_version: 1.3.6" \
    	>> "${java_managed_partials}/attributes.adoc"
	echo ":minimum_docker_version: 20.10.14" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":java_minimum_sdk_version: 3.0.0" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":console: https://console.akka.io/" \
		>> "${java_managed_partials}/attributes.adoc"

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
#	bin/bundle.sh --zip "${java_managed_attachments}/customer-registry-quickstart.zip" ../samples/customer-registry-quickstart
#	bin/bundle.sh --zip "${java_managed_attachments}/customer-registry-views-quickstart.zip" ../samples/customer-registry-views-quickstart
#	bin/bundle.sh --zip "${java_managed_attachments}/choreography-saga-quickstart.zip" ../samples/choreography-saga-quickstart

dev: clean managed validate-xrefs dev-html

# like dev but without apidocs, bundles and testkits. Useful for fast dev cycles
quick-dev: clean prepare attributes examples dev-html

done:
	@echo "Generated docs at ${TARGET_DIR}/akka-documentation/index.html"

local: docker-image examples antora-local done

antora-local:
	docker run \
		-v ${ROOT_DIR}:/antora \
		--rm \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		--cache-dir=.cache/antora --stacktrace --log-failure-level=warn docs/antora-playbook-local.yml

validate-xrefs:
	docker run \
		-v ${ROOT_DIR}:/antora \
		--rm \
		--entrypoint /bin/sh \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		--generator @antora/xref-validator dev/antora.yml

validate-links:
	docker run \
		-v ${ROOT_DIR}:/antora \
		--rm \
		--entrypoint /bin/sh \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c "cd /antora/${BASE_PATH} && find src -name '*.adoc' -print0 | xargs -0 -n1 asciidoc-link-check --progress --config config/validate-links.json"

deploy: clean managed
	bin/deploy.sh --module java --upstream ${upstream} --branch ${branch} ${sources}
