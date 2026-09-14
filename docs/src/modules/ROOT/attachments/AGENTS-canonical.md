# Akka

Akka is a platform for building and operating agentic and event-driven
services. It supplies a Java/Scala SDK (agents, workflows, entities,
views, endpoints) and a runtime that runs those services with active-
active replication, durable state, and integrated observability.

This project is an Akka SDK project. Drive it through the Akka MCP
server (`akka mcp serve`, stdio) — invoke MCP tools by name rather than
shelling out to `akka` directly. Different harnesses surface MCP tools
differently (some as `/mcp__akka__<tool>`, some as `@akka <tool>`, some
just by name in the tool list); the tool names themselves are portable.

## Spec-driven development workflow

Akka projects follow a spec → plan → tasks → implement loop. The
templates live in `.akka/templates/` and the constitution in
`.akka/constitution/`; the MCP tools below read and write into those
trees.

1. **Read the constitution first.** Call `akka_sdd_constitution` before
   writing code or a spec — it returns the Akka SDK conventions the
   project builds on.
2. **Specify.** For a new feature, fetch the spec template with
   `akka_sdd_get_template` (template `spec`) and create the spec file
   via `akka_sdd_create_spec`. List existing specs with
   `akka_sdd_list_specs`.
3. **Plan and break down.** Fetch the `plan` and `tasks` templates
   with `akka_sdd_get_template` and fill them in alongside the spec.
   `akka_sdd_list_templates` enumerates what is available.
4. **Implement.** Write code under `src/`, referring to `akka-context/`
   for the current SDK surface.
5. **Build and test locally.** Use `akka_maven_compile` to compile,
   `akka_maven_test` to run tests (pass `test_class` or `test_method`
   for a focused loop), and `akka_maven_verify` for the full lifecycle
   including integration tests.
6. **Run locally.** Start the local environment with `akka_local_start`,
   then `akka_local_run_service` to compile-and-run. Use
   `akka_local_status`, `akka_local_logs`, and `akka_local_request` to
   verify behaviour; `akka_local_stop_service` when done.
7. **Ship (only when asked).** Build the image with `akka_build_image`,
   then `akka_services_deploy` (with `push=true`) to push and deploy.
   Use `akka_services_logs` and `akka_routes_create` to finish the
   rollout.

For inspection of running services (entities, workflows, views, agent
interactions), reach for the `akka_backoffice_*` tools. `akka_refresh`
updates the on-disk skills, templates, and docs to the latest version
if they seem stale. Do **not** re-run `akka_sdd_init` — this project is
already initialized.

## Ground rules

- Read `akka-context/` before proposing SDK code. It is the source of
  truth for the current Akka SDK surface — model behaviour on what is
  there, not on general Java or Kalix knowledge.
- Read `.akka/constitution/` before implementation. Project-specific
  constraints override anything in this file.
- Iterate locally (compile → test → run) until the service works
  before touching platform-deploy tools.
