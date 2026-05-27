<!-- <nav> -->
- [Akka](../index.html)
- [Getting started & Tutorials](index.html)
- [Containerized dev environment](dev-container.html)

<!-- </nav> -->

# Containerized dev environment

If you want to try Akka without installing Java, Maven, and the Akka CLI on your machine, the Akka dev container gives you a ready-to-use environment inside Docker. It bundles everything you need: Java 21, Maven, the Akka CLI, and pre-cached SDK dependencies. You can start building in minutes.

|  | The dev container is ideal for getting started quickly. For long-term development, we recommend installing the tools directly on your machine. See [Tutorials](index.html) for the standard setup path. |

## <a href="about:blank#_prerequisites"></a> Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- An [Akka download token](https://account.akka.io/token) (free, used to download SDK dependencies)

## <a href="about:blank#_set_environment_variables"></a> Set environment variables

The Akka download token is required. AI provider keys are optional and only needed if you plan to build agent samples.

```bash
export AKKA_RESOLVER_TOKEN=<your-token>       # required
export GOOGLE_AI_GEMINI_API_KEY=<your-key>    # optional, for agent samples
export ANTHROPIC_API_KEY=<your-key>           # optional, for agent samples
export OPENAI_API_KEY=<your-key>              # optional, for agent samples
```

## <a href="about:blank#_create_a_project_and_start_the_container"></a> Create a project and start the container

1. Use the Akka CLI to create a new project from a sample:

```bash
akka code init --name helloworld-agent --repo akka-samples/helloworld-agent.git
```
2. Start the dev container with the project directory mounted:

```bash
docker run -d --name akka-dev \
  -v "$(pwd)/helloworld-agent":/workspace \
  -p 9000:9000 \
  -p 9889:9889 \
  -e AKKA_RESOLVER_TOKEN \
  -e GOOGLE_AI_GEMINI_API_KEY \
  -e ANTHROPIC_API_KEY \
  -e OPENAI_API_KEY \
  registry.akka.io/akka-dev-container:latest
```
This starts the container in the background with two forwarded ports:

  - `localhost:9000` for your Akka service endpoint
  - `localhost:9889` for the Akka local console

## <a href="about:blank#_run_the_service"></a> Run the service

All build commands run inside the container using `docker exec`. Your files are on the host. You edit them with your normal editor, and the container picks up changes immediately through the bind mount.

Run the service:

```bash
docker exec -w /workspace akka-dev mvn compile exec:java
```
Once the service is running, test it from your host:

```bash
curl -i -XPOST http://localhost:9000/hello \
  --header "Content-Type: application/json" \
  --data '{"user": "alice", "text": "Hello, I am Alice"}'
```

## <a href="about:blank#_start_the_local_console"></a> Start the local console

The Akka local console provides a web UI for inspecting your running service. Start it with:

```bash
docker exec -t -w /workspace akka-dev akka local console --bind-address 0.0.0.0
```
Then open [localhost:9889](http://localhost:9889/) in your browser.

## <a href="about:blank#_how_it_works"></a> How it works

**Host-mounted workspace** Your project directory is bind-mounted to `/workspace` in the container. You edit files on the host, the container runs builds. No file syncing needed.

**Pre-cached dependencies** Most Akka SDK dependencies are baked into the image, significantly reducing download times on first run.

**Forwarded ports** Port `9000` (Akka service) and `9889` (local console) are exposed to your host machine.

## <a href="about:blank#_next_steps"></a> Next steps

Once you are comfortable with Akka, install the tools directly on your machine for the best development experience:

- [Install the Akka CLI](quick-install-cli.html)
- Follow the [Hello world agent](author-your-first-service.html) tutorial
- Explore [Spec-Driven Development](../sdk/spec-driven-development.html) for AI-assisted workflows

<!-- <footer> -->
<!-- <nav> -->
[Authenticated user-specific lookup](shopping-cart/addview.html) [Additional samples](samples.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->