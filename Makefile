# Make Akka SDK for Java documentation

upstream := lightbend/akka-javasdk
branch   := docs/current
sources  := src build/src/managed

src_managed := build/src/managed

java_managed_attachments := ${src_managed}/modules/java/attachments
java_managed_examples := ${src_managed}/modules/java/examples
java_managed_partials := ${src_managed}/modules/java/partials

antora_docker_image := gcr.io/kalix-public/kalix-docbuilder
antora_docker_image_tag := 0.0.7
root_dir := $(shell git rev-parse --show-toplevel)
base_path := $(shell git rev-parse --show-prefix)

.SILENT:

build: dev

clean:
	rm -rf build

prepare:
	mkdir -p "${src_managed}"
	cp src/antora.yml "${src_managed}/antora.yml"

managed: prepare attributes apidocs examples bundles

attributes:
	mkdir -p "${java_managed_partials}"
	bin/version.sh | xargs -0  printf ":akka-javasdk-version: %s" \
		> "${java_managed_partials}/attributes.adoc"
	echo ":java-pb-version: 11" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":java-version: 17" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":minimum_maven_version: 3.6" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":minimum_sbt_version: 1.3.6" \
    	>> "${java_managed_partials}/attributes.adoc"
	echo ":minimum_docker_version: 20.10.14" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":java_minimum_sdk_version: 0.7.0" \
		>> "${java_managed_partials}/attributes.adoc"
	echo ":console: https://console.kalix.io/" \
		>> "${java_managed_partials}/attributes.adoc"

apidocs:
	mkdir -p "${java_managed_attachments}"
	cd .. && sbt akka-javasdk/doc akka-javasdk-testkit/doc
	rsync -a ../akka-javasdk/target/api/ "${java_managed_attachments}/api/"
	rsync -a ../akka-javasdk-testkit/target/api/ "${java_managed_attachments}/testkit/"
	bin/version.sh > "${java_managed_attachments}/latest-version.txt"

examples:
	mkdir -p "${java_managed_examples}"
	rsync -a --exclude-from=.examplesignore ../samples/* "${java_managed_examples}/"

bundles:
	bin/bundle.sh --zip "${java_managed_attachments}/customer-registry-quickstart.zip" ../samples/customer-registry-quickstart
	bin/bundle.sh --zip "${java_managed_attachments}/customer-registry-views-quickstart.zip" ../samples/customer-registry-views-quickstart
	bin/bundle.sh --zip "${java_managed_attachments}/shopping-cart-quickstart.zip" ../samples/shopping-cart-quickstart
	bin/bundle.sh --zip "${java_managed_attachments}/choreography-saga-quickstart.zip" ../samples/choreography-saga-quickstart

dev: clean managed validate-xrefs dev-html

# like dev but without apidocs, bundles and testkits. Useful for fast dev cycles
quick-dev: clean prepare attributes examples dev-html

local-html:
	antora --fetch --stacktrace local.yml
	@echo "local done, generated docs at ./build/site/index.html"

dev-html:
	docker run \
		-v ${root_dir}:/antora \
		--rm \
		--entrypoint /bin/sh \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c "cd /antora/${base_path} && antora --cache-dir=.cache/antora --stacktrace --log-failure-level=warn dev/antora.yml"
	@echo "Generated docs at dev/build/site/java/index.html"

validate-xrefs:
	docker run \
		-v ${root_dir}:/antora \
		--rm \
		--entrypoint /bin/sh \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c 'cd /antora/${base_path} && NODE_PATH="$$(npm -g root)" antora --generator @antora/xref-validator dev/antora.yml'

validate-links:
	docker run \
		-v ${root_dir}:/antora \
		--rm \
		--entrypoint /bin/sh \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c "cd /antora/${base_path} && find src -name '*.adoc' -print0 | xargs -0 -n1 asciidoc-link-check --progress --config config/validate-links.json"

deploy: clean managed
	bin/deploy.sh --module java --upstream ${upstream} --branch ${branch} ${sources}
