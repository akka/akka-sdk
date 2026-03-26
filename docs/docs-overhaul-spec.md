# Akka Documentation Overhaul Specification

## Project Goal
Review and improve Akka's existing SDK documentation at doc.akka.io to:
1. Align with Akka's revised positioning: "Reliable AI for Every Industry. The Agentic Systems Platform."
2. Frame content around the **three barriers** (Production Gap, Liability, Specialist Trap) and **three dimensions** (Reliability, Risk Control, Repeatability)
3. Lead with Spec-Driven Development (SDD) instead of manual/hand-coding approach
4. Organize content by persona: Builders, Operators, Governance & Oversight
5. Add use-case-driven docs alongside existing capability-driven component docs
6. Make content discoverable for both humans and AI agents
7. Reflect governance-first positioning — "built in, not bolted on"

**Why:** Akka has evolved from a developer-install-first product to a full-stack agentic systems platform (developer experience + runtime + governance). The revised positioning sharpens around three barriers customers face and three dimensions Akka solves. Docs must reflect this.

**How to apply:** All content should be framed through the lens of the three dimensions. Lead with SDD, treat manual setup as fallback. Content should address personas explicitly. Governance and compliance should be woven throughout (not siloed into a single section). Competitive differentiation (vs Temporal, LangChain, n8n) should inform "Why Akka" content.

## Positioning Source
Canonical positioning lives in `the corporate llms.txt (external to this repo)` (revised 2026-03).

### Core Message
"Akka is the agentic systems platform built for Reliability, Risk Control, and Repeatability at enterprise scale."

### Three Barriers (the "why")
1. **Production Gap** — AI frameworks built for demos can't deliver durability, resilience, and reliability for production
2. **Liability** — Failures in resilience, governance, and safety carry severe legal, financial, and reputational consequences
3. **Specialist Trap** — Consistent delivery breaks down when agentic AI depends on a handful of experts

### Three Dimensions (the "how")
1. **Reliability** — 99.9999% availability, active-active HA/DR, sub-1 min RTO, zero byte RPO, contractual commitments with indemnities
2. **Risk Control** — Runtime-embedded governance, policy enforcement, self-explanation, self-containment, EU AI Act compliance
3. **Repeatability** — Multi-tenant platform, golden-path workflows, SDD so anyone who can write requirements can build

### Full-Stack Platform Positioning
"The only full stack platform for agentic AI: developer experience, runtime, and governance in one integrated system."

### Key Differentiators
- Bolt-on governance fails (immutable records, human intervention, authorization capture, PII scrubbing arguments)
- Shared compute model cuts infrastructure costs up to 90%
- Sub-10ms memory (vs ~200ms bolt-on)
- 18 years, 100K+ deployments, 2B+ daily users
- 19+ compliance certifications including EU AI Act, ISO 42001, SOC 2

### Competitive Landscape
- **Temporal** — Orchestration only. No agents, memory, governance, or AI capabilities.
- **LangChain** — Developer framework, not a platform. No runtime, HA/DR, or governance.
- **n8n** — Workflow automation tool. No enterprise scale, clustering, or compliance.

### Service Tiers
- **Fast Prod** — Private multi-tenant install in your VPC, production-ready from day one
- **Day 2 Ops** — Adds elastic scaling, live CVE patching, rolling updates, explainability
- **Business Continuity** — Active-active HA/DR across regions/clouds
- **Sovereign Cloud** — Country-isolated, all data stays in-region

### Personas

**Builders** (docs primary audience)
- Application Developers — broadest group, range from senior to no distributed systems background
- AI/ML Engineers — agent design, prompt engineering, model selection, guardrail tuning
- Product Managers / Business Analysts — author specs not code via SDD

**Operators**
- Platform Engineers / PlatformOps — own installation, manage tenants, enforce golden paths
- SREs / DevOps — availability, HA/DR, scaling, incidents

**Governance & Oversight**
- Compliance Analysts — explainability, interaction logging, causal analysis
- InfoSec Engineers — guardrails, policy enforcement, access controls
- FinOps — token tracking, cost optimization

### TCO Arguments (for "Why Akka" content)
- Infrastructure: shared compute, scale-to-zero, consolidated platform
- Operations: managed HA/DR, no-downtime updates, live CVE patching, AAO
- Token/AI: AdaptiveML integration, memory compaction, FinOps tooling, runtime guardrails
- People: specialist trap elimination, wider builder pool, consistent patterns
- Compliance: built-in governance, runtime policy enforcement, inherited certifications

## Scope
- Akka SDK docs (doc.akka.io) — IN SCOPE
- Akka Libraries docs — OUT OF SCOPE (always treated separately)

## Key Principles
1. **AI-first documentation** — The primary consumer is an AI agent as much as a human. Structure, label, and write content so agents can parse, navigate, and act on it. Prefer explicit structure (clear headings, consistent patterns, machine-readable metadata) over prose-heavy narrative. Every page should be useful to an agent retrieving context for a developer.
2. **SDD-first** — Lead with Spec-Driven Development; hand-coding is secondary/fallback
3. Frame around three barriers and three dimensions throughout
4. Materials are educational — teach users how to develop enterprise services
5. Specific SDK details remain available for reference
6. Use-case driven docs alongside capability/component docs
7. Persona-aware: content should speak to Builders, Operators, and Governance audiences
8. Governance woven throughout, not just one section
9. **External links must be machine-identifiable** — All external links use Antora attributes defined in a central registry file (see Platform Implementation Details). A script or agent must be able to find every external link across all doc pages and update it in one pass if the URL changes.

## Writing Standards & Style Guide

Based on analysis of existing Components section (full analysis in `docs/akka_docs_style_analysis.md (in the planning workspace)`).

### Voice & Tone

1. **Second-person ("you") only.** Drop "we" entirely. Minimize passive voice. The reader is "you"; Akka is "Akka" (not "we").
   - Yes: "You define state using a Java record."
   - Yes: "Akka persists events automatically."
   - No: "We use component IDs to identify the class."
   - No: "The handling of a command, if it results in changes being required to state, should persist events."

2. **Imperative for instructions, declarative for facts.**
   - Instruction: "Annotate the class with `@Component`."
   - Fact: "Events are guaranteed to be delivered at least once."

3. **Medium formality.** Technical and precise, not academic. Avoid contractions in body text. No humor, colloquialisms, or filler.

4. **Short sentences preferred.** Break compound-complex sentences into two. Lead with the action or fact, not the condition.
   - Yes: "Persist events from your command handler. Akka applies them to update state."
   - No: "The handling of a command, if it results in changes being required to state, should persist events."

5. **Define jargon on first use.** Every domain term (passivation, sharding, Effects, CloudEvents) must be defined or linked to the Glossary on the page where it first appears.

### SDD-First / Manual Fallback Handling

On pages that cover both SDD and manual/hand-coded approaches:
- **SDD is the primary content path.** It occupies the main body of the page.
- **Manual/hand-coded approach uses AsciiDoc admonition blocks**, clearly labeled. Use the `TIP:` or custom `[NOTE.manual]` admonition style:
  ```asciidoc
  [TIP.manual]
  .Without SDD
  ====
  To create this component manually without SDD, ...
  ====
  ```
  The existing Antora setup already supports `TIP:`, `NOTE:`, `IMPORTANT:` admonitions. A custom role class (`.manual`) can be styled distinctly via the supplemental UI CSS.
- Never present manual-first with SDD as the aside. The default reader experience is SDD.

### Page Structure Template

Every component/feature page MUST follow this skeleton:

```
# [Component Name]                          ← H1, noun form (not "Implementing X")

> One-sentence definition of what this component IS.

## Overview
- What it does (1-2 sentences)
- When to use it (bullet list of use cases)
- When NOT to use it / alternatives (with links)
- How it relates to other components

## [SDD Path]                               ← Primary: how to create/use via SDD
- Spec example
- What Akka generates
- How to customize

## [Core Sections]                          ← Varies by component type
- Modeling state
- Implementing behavior / Defining routes / etc.
- Effect API (where applicable)
- Error handling

## Testing
- Unit tests
- Integration tests

## Advanced Topics                          ← Optional
- Multi-region replication (where applicable)
- Side effects
- Performance considerations

## See Also
- Related component pages (explicit links)
- Conceptual explanation (link to Understanding)
- External content (blog posts, demos, webinars — using link registry)
- API reference (Javadoc)
```

**Non-negotiable sections:** Every page must have Overview (with definition, when-to-use, and relationships), Testing, and See Also. No page ships without these.

### Heading Standards

1. **H1: Noun form.** "Agents", "Workflows", "Event Sourced Entities" — not "Implementing Agents".
2. **H2+: Self-descriptive without page context.** A heading must make sense if extracted into a search result or agent index.
   - Yes: "Compensating Failed Workflow Steps"
   - No: "Compensation"
   - Yes: "Basic Agent Structure"
   - No: "Skeleton"
3. **Consistent naming across pages.** If one page has "Modeling State", all entity pages use "Modeling State" (not "Defining State" or "State Model").

### Code Examples

1. **Code-before-explanation.** Show the code block first, then explain with numbered callouts below.
2. **Numbered callout format:** AsciiDoc `<1>`, `<2>`, `<3>` callouts in source with explanations below — preserve the existing convention used throughout the repo.
3. **Always pull from compilable source.** Use the existing `include::example$...` pattern with tagged regions. Never write code inline in AsciiDoc — all examples must compile in the `samples/` directory.
   ```asciidoc
   [source,java,indent=0]
   ----
   include::example$doc-snippets/src/main/java/com/example/MyAgent.java[tag=class]
   ----
   ```
4. **Always include essential imports** in the first example on a page. Use a separate tag for the import block if needed.
5. **Add a one-line comment at the top** of each tagged region identifying what it demonstrates.
6. **Link to complete working sample** when examples span multiple classes. Use the `{sample-base-url}` attribute (already defined in `partials/include.adoc`) to link to the GitHub source.
7. **Keep examples under 30 lines** where possible. Extract longer examples into linked sample repos.

### AI-First Formatting

1. **Structured metadata via Antora page attributes** at the top of every page (after the `include::ROOT:partial$include.adoc[]` line):
   ```asciidoc
   = Agents
   :page-component-type: agent
   :page-summary: An Agent interacts with an AI model to perform a specific task, maintaining session memory and supporting multi-agent collaboration.
   :page-when-to-use: LLM-backed tasks, conversational AI, multi-agent orchestration
   :page-related: sdk:workflows.adoc, sdk:event-sourced-entities.adoc, concepts:ai-agents.adoc
   :page-prerequisites: Java 21, Akka SDK basics
   :page-persona: builder-developer, builder-ai-ml
   ```
   The `page-` prefix makes these available to the Antora UI templates and to the `llms-txt` generator. They are also extractable by any script that parses AsciiDoc front matter.

2. **Every page must have an extractable definition** in the first sentence after the title. An agent should be able to read the first paragraph and know what the component does, when to use it, and what it relates to.

3. **Headings serve as an API for the page.** An agent reading only the heading tree should understand the page's structure and be able to jump to the right section.

4. **No context-dependent references.** Don't write "as described above" or "the class we defined earlier." Name the thing: "the `OrderEntity` class defined in xref:sdk:event-sourced-entities.adoc#modeling-state[Modeling State]."

5. **Explicit component relationships.** Every component page must state which other components it commonly pairs with and link to them using Antora xrefs. Don't rely on the reader having read all pages.

### Content Deduplication

- **Use Antora partials** for shared content. The repo already has `sdk/partials/entity-sharding.adoc`, `entity-ids.adoc`, `testing-entity.adoc`, etc. Any content that appears on multiple pages must be a partial included via `include::partial$...[]`.
- **Shared concepts** (sharding, passivation, multi-region replication, at-least-once delivery) live in Understanding pages and are linked from component pages — not copy-pasted.
- **When a concept is critical to a component page**, include a 1-2 sentence summary with an xref to the full explanation. Don't reproduce the full section.

### Existing Page Naming Conventions to Standardize

| Current | Standard |
|---------|----------|
| "Implementing agents" | "Agents" |
| "Implementing Workflows" | "Workflows" |
| "Implementing Event Sourced Entities" | "Event Sourced Entities" |
| "Designing HTTP Endpoints" | "HTTP Endpoints" |
| "Consuming and Producing" | "Consumers" |
| "Timers" | "Timed Actions" |

## Platform Implementation Details

Source: `https://github.com/akka/akka-sdk/tree/main/docs`

### Platform & Tooling
- **Antora** with **AsciiDoc** (`.adoc` files)
- 523 pages across 7 modules: ROOT, getting-started, concepts, sdk, operations, reference, libraries
- Custom Antora UI bundle at `docs/supplemental_ui/`
- Vale linting (Microsoft + write-good styles) — extend with custom rules to enforce writing standards
- Link validation via `asciidoc-link-check`
- Built via Docker, deployed via rsync to `gustav.akka.io` (behind `doc.akka.io`)

### Repository Structure
```
docs/src/
  antora.yml                    ← Component descriptor, lists nav files in order
  modules/
    ROOT/pages/, images/, partials/   ← Landing page, shared attributes, shared partials
    getting-started/pages/, images/   ← Tutorials
    concepts/pages/, images/          ← Understanding / Concepts
    sdk/pages/, images/, partials/    ← Developing / SDK components
    operations/pages/, images/        ← Operating
    reference/pages/, images/         ← Reference (incl. auto-generated CLI docs)
    libraries/pages/                  ← Thin landing page (out of scope)
docs/src-managed/                     ← GENERATED at build time (examples, API docs, attributes)
docs/supplemental_ui/                 ← Custom theme overrides
docs/bin/                             ← Build/deploy scripts
samples/                              ← Compilable sample projects (source of truth for code snippets)
```

### Navigation
Defined in per-module `nav.adoc` files, referenced from `antora.yml` in order. Our restructure involves:
1. Adding new modules or sections for Why Akka, What is Akka, Who Uses Akka, Resources
2. Editing `antora.yml` to reorder the nav file list
3. Editing individual `nav.adoc` files for section-level changes

### External Link Registry
All external URLs defined as Antora attributes in a **single central file**: `docs/src/modules/ROOT/partials/external-links.adoc` (new file). Included by every page via the existing `include::ROOT:partial$include.adoc[]` chain.

```asciidoc
// -- akka.io product pages --
:url-akka-platform: https://akka.io/akka-agentic-ai-platform
:url-akka-agents: https://akka.io/akka-agents
:url-akka-orchestration: https://akka.io/akka-orchestration
:url-akka-memory: https://akka.io/akka-memory
:url-akka-streaming: https://akka.io/akka-streaming
:url-akka-aao: https://akka.io/automated-operations
:url-akka-how-it-works: https://akka.io/how-akka-works
:url-akka-pricing: https://akka.io/pricing

// -- akka.io blog posts --
:url-blog-agents: https://akka.io/blog/akka-agents-quickly-create-agents-mcp-grpc-api
:url-blog-orchestration: https://akka.io/blog/akka-orchestration-guide-moderate-and-control-agents
:url-blog-what-is-agentic: https://akka.io/blog/what-is-agentic-ai
// ... (complete list from content inventory)

// -- external / foundational --
:url-reactive-manifesto: https://www.reactivemanifesto.org
:url-reactive-principles: https://www.reactiveprinciples.org
:url-akkademy: https://akkademy.akka.io

// -- customer stories --
:url-story-swiggy: https://akka.io/blog/2x-latency-improvement-in-swiggy-ml-and-ai-platform
:url-story-manulife: https://akka.io/blog/manulife-selects-akka-to-operationalize-agentic-ai
// ... (complete list from content inventory)
```

Usage in pages:
```asciidoc
See the link:{url-blog-agents}[Akka Agents deep-dive, window="new"] for architecture details.
```

To update a URL: change it in one place (`external-links.adoc`), every page picks it up.

### Existing Attributes & Partials
- **Version attributes** generated at build time into `src-managed/modules/ROOT/partials/attributes.adoc`: `{akka-javasdk-version}`, `{akka-cli-version}`, `{java-version}`, etc.
- **Static attributes** in `ROOT/partials/include.adoc`: `{sample-base-url}` (GitHub samples link)
- **Every page starts with** `include::ROOT:partial$include.adoc[]` — this is where the external link registry gets included
- **SDK partials**: `entity-sharding.adoc`, `entity-ids.adoc`, `testing-entity.adoc`, `component-endpoint.adoc` — use these for deduplication

### LLM Output (llms.txt standard — llmstxt.org)

Follows the **llms.txt specification** created by Jeremy Howard (fast.ai/Answer.AI, Sep 2024). Both files are **fully auto-generated** every time the Antora docs are built. No hand-maintained files. The existing `docs/src/modules/ROOT/pages/llms.txt` (currently hand-maintained) is replaced by generated output. The build pipeline (`docs-prod.yml`) handles rsyncing to `doc.akka.io`.

**Standard file names and purposes:**

- **`/llms.txt`** — A curated **index** in Markdown format. Contains a project summary, organized H2 sections of links (`- [Title](url): description`), and an `## Optional` section for supplementary content that can be dropped when context is tight. An agent reads this to understand what docs are available, then selectively fetches only the pages it needs. Small file (1-5 KB).

- **`/llms-full.txt`** — All documentation content **inlined into a single file**. No links to follow — everything is right there. For agents that want to stuff the entire doc set into context at once. Large file (hundreds of KB to MB).

**Note:** The repo currently also generates `llms-ctx.txt` and `llms-ctx-full.txt` (XML-wrapped format from the `llms-txt` Python tool). These are not part of the core llms.txt standard. Decide whether to keep them for backwards compatibility or drop in favor of the standard file names.

**Generation approach:** Extend the existing `llms-txt` Python package usage in the build pipeline, or write a custom Antora extension / post-build script that:
1. Reads the positioning preamble from a template file (practitioner-filtered subset of corporate llms.txt)
2. Walks all built pages, extracts `page-` attributes to build the link index
3. Assembles `llms.txt` per the standard structure (H1 → blockquote → context → H2 sections of links → ## Optional)
4. Assembles `llms-full.txt` by inlining all page content
5. Outputs to the build target alongside the site

### Admonition Types Available
Already supported in the Antora setup:
- `NOTE:` — general information
- `TIP:` — helpful suggestions
- `IMPORTANT:` — critical information
- `CAUTION:` / `WARNING:` — danger/risk
- Custom roles via `[NOTE.manual]` etc. — styled via supplemental UI CSS
- `ifdef::review[...]` and `ifdef::todo[...]` — conditional content visible only in local builds

### Vale Linting Extensions
Add custom Vale rules to enforce writing standards:
- Flag first-person "we" usage
- Flag passive voice
- Flag non-self-descriptive headings (e.g., single-word H2s like "Skeleton", "Basics")
- Flag inline external URLs not using an attribute (enforce link registry usage)

## Agreed Doc Structure (Full Outline)

### Top-Level Nav (reordered)
Why Akka → What is Akka → Tutorials → Developing → Understanding → Operating → Resources → Reference

### Why Akka (NEW — landing entry point)
- The three barriers: Production Gap, Liability, Specialist Trap
- How Akka solves them: Reliability, Risk Control, Repeatability
- Brief competitive context (what's different vs Temporal, LangChain, n8n)
- Should be concise and high-signal — sets the frame, not a deep dive

### What is Akka (NEW — brief platform intro)
- Full-stack platform: developer experience + runtime + governance
- Key capabilities at a glance (SDD, SDK, runtime, governance, AAO)
- Service tiers overview (Fast Prod → Sovereign Cloud)
- Links forward into Tutorials and Developing

### Who Uses Akka (NEW — persona-based navigation, under What is Akka)
A single page listing every persona with curated links to the docs they care about. Serves as a routing page for both humans and agents.

**Builders**
- Application Developers → SDD, Tutorials, SDK components, Use cases, Integrations, Manual setup
- AI/ML Engineers → Agents, Guardrail tuning, Model integrations, Governance config
- Product Managers / Business Analysts → SDD, Spec authoring

**Operators**
- Platform Engineers / PlatformOps → Deployment models, AAO, Tenant management, Golden paths
- SREs / DevOps → HA/DR, Multi-region & resilience, Observability, Scaling, CLI reference

**Governance & Oversight**
- Compliance Analysts → Governance & the runtime, Explainability, Interaction logging, Causal analysis
- InfoSec Engineers → Policy enforcement, Guardrails, Access controls, Security & compliance
- FinOps → Token tracking, Cost optimization, Observability

### Tutorials
- Getting Started: Build first agent (SDD - PRIMARY), Hello World (classic - secondary)
- SDD Tutorials: First agent w/SDD, Multi-agent planner, RAG chat agent, AI with durable memory
- Advanced / Deep Dive: Shopping cart, Knowledge indexing, Additional samples

### Developing
1. Spec-Driven Development (TOP - primary path, see SDD visual requirements below)
2. Components — **reference-style, not educational.** These pages document the SDK programming model component by component (Agents, Workflows, ESE, KVE, Endpoints, Views, Consumers, Timed Actions). No major structural changes planned to existing component pages — they serve their purpose as reference. Improvements limited to: adding Overview (definition, when-to-use, relationships), Testing, and See Also sections where missing; standardizing headings and voice per writing standards.
3. Use Cases (new section - see below)
4. Integrations (new section - see below)
5. Manual Setup (FALLBACK path - not primary)
   - Overview, Prerequisites, CLI install, Repo config, Project creation, Structure,
     Building, Testing, Running locally, Running local cluster

**Note on Developing vs. Tutorials:** Tutorials are educational — guided, end-to-end walkthroughs that teach by building something specific. The Developing section is where you go once you know what you're building — SDD to get started, component reference to look things up, use cases for patterns, integrations for connecting to external systems. The educational "how to think about building with Akka" content lives in Tutorials; Developing is the workbench.

#### SDD Installation Flow
The SDD page must guide users to install in this order:
1. **Primary: AI Marketplace plugin** — Point users to the relevant AI marketplace for their tool (Claude Code, Cursor, etc.) to install the Akka plugin. This is the fastest path.
2. **Fallback: Akka CLI** — Only if the marketplace install doesn't work or isn't available for their tool, fall back to installing the Akka CLI and using `akka specify init`.

This mirrors the SDD-first / manual-fallback principle: marketplace is the golden path, CLI is the fallback. Use a callout box (admonition) for the CLI fallback approach.

#### SDD Section Visual Requirements
The Spec-Driven Development section is the primary entry point for Developing and must be supported with strong visuals:
- **SDD workflow diagram** — Visual showing the end-to-end flow: write spec → Akka generates system → review/customize → deploy. Should make immediately clear what SDD is and how it differs from manual development.
- **Before/after comparison** — Visual or side-by-side showing the same outcome achieved via SDD (spec + generation) vs. manual (code all components by hand). Reinforces why SDD is the primary path.
- **Spec-to-system mapping** — Visual showing how parts of a spec map to generated components (spec requirement → Agent, Workflow, Entity, Endpoint, etc.). Helps users understand what Akka produces from their spec.
- **SDD iteration cycle** — Visual showing the iterative loop: spec → generate → test → refine spec → regenerate. Shows SDD is not a one-shot process.
- Visuals should be created as SVG or similar format that renders well for both humans and can have alt-text/descriptions for agent consumption.

### Use Cases (new section under Developing)
Each use case page includes: pattern description, which Akka components are involved, and a walkthrough of a real sample project showing how the system is composed. Code is pulled from `samples/` via `include::example$...` — no fabricated snippets. Use cases without a backing sample ship as stubs (pattern + component list, no code walkthrough) until a real example exists.

| Use Case | Status | Backing Sample(s) |
|----------|--------|-------------------|
| Conversational AI | Ready | `helloworld-agent`, `customer-service-chat`, `travel-planning-agent` |
| Autonomous Agents | Ready | `release-note-summarizer`, `medical-discharge-tagging`, `iot-sensor-monitoring` |
| Multi-Agent Systems | Ready | `multi-agent`, `customer-service-chat` |
| Memory & State | Ready | `travel-planning-agent`, `shopping-cart-quickstart`, `key-value-customer-registry`, `event-sourced-customer-registry` |
| RAG & Knowledge | Ready | `ask-akka-agent` |
| Streaming AI | Ready | `iot-sensor-monitoring`, `event-sourced-customer-registry-subscriber` |
| Orchestration & Durability | Ready | `transfer-workflow`, `transfer-workflow-orchestration`, `transfer-workflow-compensation`, `choreography-saga-quickstart`, `reliable-timers` |
| APIs & Exposure | Ready | `endpoint-jwt`, `trip-booking-with-tools` |
| Governance & Compliance | Partial | `medical-discharge-tagging` (human verification), `transfer-workflow-compensation` (compensation), `tracing` (observability). Pattern guide can be written; may need a dedicated governance-focused sample for full walkthrough. |
| Enterprise Patterns | Ready | `shopping-cart-quickstart`, `choreography-saga-quickstart`, `spring-dependency-injection` |

### Integrations (new section under Developing)
Organized by role, not technology. Key framing: Akka runs on the JVM — all integrations use **industry-standard Java APIs and client libraries**. No proprietary adapters or Akka-specific wrappers needed. If a Java client exists for it, you can use it in Akka.

- **AI & Models:**
  - LLM providers (built-in — 9 providers supported natively): Anthropic, OpenAI, Google AI Gemini, Google Cloud Vertex AI, AWS Bedrock, Ollama, LocalAI, Hugging Face, plus custom providers via `ModelProvider.Custom` interface. **Link to existing docs** — already well-documented at [Agents](https://doc.akka.io/java/agents.html) and [Model Provider Details](https://doc.akka.io/java/model-provider-details.html). Do not duplicate.
  - Embedding models — gap in current docs, needs new content
  - Model selection guidance — gap in current docs (which model for which use case)
- **Data & Knowledge:**
  - Vector databases: Pinecone, Weaviate, Qdrant, Chroma, pgvector — via their Java client libraries
  - Relational databases: PostgreSQL, MySQL — via JDBC
  - NoSQL databases: MongoDB, DynamoDB, Cassandra — via their Java client libraries
  - Search engines: Elasticsearch, OpenSearch — via their Java client libraries
  - **No caching layer needed.** Akka's entity components (Event Sourced Entities, Key Value Entities) are in-memory systems of durable record — all data is durable, immutable, and in-memory with sub-10ms access. There is no need for a separate cache like Redis. This should be called out explicitly as a differentiator and a common question answered.
- **Messaging & Events:**
  - **Akka-native first.** Akka has built-in messaging capabilities (brokerless pub/sub, service-to-service eventing, event-driven consumers) that should be the primary path. These are durable, integrated with the runtime, and require no external infrastructure.
  - External message brokers (when needed for integration with systems outside Akka): Kafka, Google Pub/Sub, Azure Event Hubs — via their Java client libraries
  - Webhooks for external system callbacks
- **APIs & Protocols:**
  - REST/HTTP endpoints (built-in)
  - OpenAPI schema generation (built-in — see [HTTP Endpoints: OpenAPI](https://doc.akka.io/sdk/http-endpoints.html#_openapi_endpoint_schema))
  - WebSockets (built-in — see [HTTP Endpoints: WebSocket](https://doc.akka.io/sdk/http-endpoints.html#websocket))
  - gRPC (built-in)
  - Agent protocol support — **A2A, ACP, and MCP clients are baked into Akka's Agent component**. Agents natively consume tools and communicate with other agents via all three protocols. No external libraries needed.
  - MCP server (built-in — MCP Endpoints component exposes Agent functions to remote LLMs via `@FunctionTool` annotations)
  - MCP CLI server (built-in — `akka mcp serve` exposes CLI operations as MCP tools for Claude Desktop, VS Code, Cursor)
  - See also: [MCP, A2A, ACP: What Does It All Mean?](https://akka.io/blog/mcp-a2a-acp-what-does-it-all-mean)
- **Identity & Security:**
  - Akka-native secret management (built-in — project-level secrets injected as env vars, supports generic secrets, symmetric/asymmetric keys, TLS certs/CA bundles, key rotation without env var changes, values never exposed in CLI/Console). See [Manage secrets](https://doc.akka.io/operations/projects/secrets.html)
  - External secrets: Azure KeyVault integration (currently documented). See [Manage external secrets](https://doc.akka.io/operations/projects/external-secrets.html)
  - Other external providers (AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault) — via standard Java APIs
- **Observability:**
  - OpenTelemetry metrics and logs exporting (built-in) — feed into any OTEL-compatible backend (Grafana, Datadog, New Relic, etc.)
- ~~Notifications~~ — Removed. Sending notifications (email, SMS, Slack) is just a standard Java API call to the relevant service. No Akka-specific integration pattern needed; covered by the general "standard Java APIs" framing.

### Understanding
- Agentic AI Concepts
- Architecture (updated — full-stack platform framing)
- Governance & the Runtime (NEW — why bolt-on fails, EU AI Act alignment)
- Distributed Systems
- Multi-Region & Resilience
- Communication Patterns
- Development Process

### Operating
- Deployment Models (aligned with service tiers: Fast Prod, Day 2 Ops, Business Continuity, Sovereign Cloud)
- Self-Managed Operations
- Akka Automated Operations (AAO)
- Observability & Monitoring (persona-aware: devs get tracing, ops gets control tower, compliance gets explainability, FinOps gets token tracking)
- Security & Compliance (elevated — runtime policy enforcement, guardrails, 19+ certifications)
- CI/CD Integration
- CLI Reference

### Resources (NEW — top-level section, aggregates all external content)
A browsable collection of all non-docs technical content, organized by type:
- Foundational: Reactive Manifesto, Reactive Principles, Jonas Boner's work, Reactive Design Patterns book
- Product Pages: Platform overview, Agents, Orchestration, Memory, Streaming, AAO, How It Works, Pricing
- Application Types: Agentic AI, Event-Sourced, Transactional, Digital Twins, Durable Execution, Analytics, Streaming, Edge
- Blogs & Articles: Conceptual, architecture, protocols, deep-dives (organized by topic)
- Demos & Videos: All demo walkthroughs and platform videos
- Talks & Podcasts: Conference talks, podcast appearances, webinars
- Customer Stories: Organized by industry vertical
- Training: Akkademy courses (deferred — catalog TBD)
- Community: Discord, GitHub, social channels
- Benchmarks: Performance benchmark results

### Reference
- Glossary (expanded with AI terms + three-barriers/three-dimensions terminology)
- API docs, Config reference, View query syntax
- Release notes, Security announcements
- llms.txt + llms-full.txt (new - for AI agent consumption, see spec below)

### llms.txt Specification (for doc.akka.io)

**Fully auto-generated** at every Antora build. Follows the llms.txt standard (llmstxt.org). The generator assembles from two sources:

1. **Positioning preamble template** — A static template file in the repo containing the practitioner-filtered subset of the corporate llms.txt. Updated manually only when corporate positioning changes.
2. **Page metadata** — Extracted automatically from `:page-*:` attributes on every built page.

**Positioning preamble includes (filtered for practitioners):**
- Core positioning: three barriers, three dimensions — so agents frame answers correctly
- Platform overview: what Akka is, full-stack positioning — so agents understand the whole, not just SDK parts
- Key differentiators: governance built-in, sub-10ms memory, shared compute, 99.9999% availability — so agents can explain "why Akka"
- Personas (Builders, Operators, Governance & Oversight) — so agents tailor responses by role
- Competitive landscape (brief): Temporal, LangChain, n8n — agents get asked "how is this different?"
- Customer stories (brief, by vertical) — social proof when agents recommend patterns

**Positioning preamble excludes (stays only in corporate llms.txt):**
- Buying personas (CAIO, CFO, procurement, legal)
- TCO breakdown details (sales context)
- Service tier pricing specifics
- Partner list
- Professional services details

**Auto-generated sections (from page metadata):**
- SDK component links — pages with `:page-component-type:` grouped and described
- Getting started links by persona — pages tagged with `:page-persona:` grouped by role
- Use case links — use case pages listed with summaries
- Integration links — integration pages listed with summaries
- Full doc site map — every page's URL + `:page-summary:`

**Generated `llms.txt` structure (per llmstxt.org standard):**
```markdown
# Akka SDK Documentation

> Build, operate, and govern agentic AI systems on the Akka platform.
> Akka is the agentic systems platform built for Reliability, Risk Control,
> and Repeatability at enterprise scale.

[Positioning context paragraphs — three barriers, three dimensions,
key differentiators, personas, competitive landscape.
No headings in this block per the standard.]

## Getting Started

- [Build Your First Agent (SDD)](https://doc.akka.io/...): Step-by-step tutorial using Spec-Driven Development
- [Hello World (Classic)](https://doc.akka.io/...): Traditional manual setup tutorial
- [Multi-Agent Planner](https://doc.akka.io/...): Build a multi-agent planning system
...

## SDK Components

- [Agents](https://doc.akka.io/...): AI model-backed components with session memory and multi-agent collaboration
- [Workflows](https://doc.akka.io/...): Long-running, multi-step business processes with compensation
- [Event Sourced Entities](https://doc.akka.io/...): State persistence via immutable event log
- [Key Value Entities](https://doc.akka.io/...): State persistence via full-state snapshots
...

## Use Cases

- [Conversational AI](https://doc.akka.io/...): Patterns for building conversational agents
- [RAG & Knowledge](https://doc.akka.io/...): Retrieval-augmented generation patterns
...

## Integrations

- [AI & Models](https://doc.akka.io/...): LLM providers, embedding models
- [Data & Knowledge](https://doc.akka.io/...): Vector DBs, relational DBs, NoSQL
...

## Concepts

- [Agentic AI Concepts](https://doc.akka.io/...): Foundational concepts for agentic systems
- [Architecture](https://doc.akka.io/...): Full-stack platform architecture
- [Governance & the Runtime](https://doc.akka.io/...): Why bolt-on governance fails
...

## Operating

- [Deployment Models](https://doc.akka.io/...): Fast Prod, Day 2 Ops, Business Continuity, Sovereign Cloud
- [Observability & Monitoring](https://doc.akka.io/...): Tracing, metrics, explainability
...

## Reference

- [Glossary](https://doc.akka.io/...): Terms and definitions
- [CLI Reference](https://doc.akka.io/...): Full Akka CLI command reference
- [Release Notes](https://doc.akka.io/...): Latest updates and changes
...

## Optional

- [Customer Stories](https://akka.io/customer-stories): Production deployments by industry
- [Reactive Manifesto](https://www.reactivemanifesto.org): Foundational philosophy behind Akka
- [Reactive Principles](https://www.reactiveprinciples.org): Cloud-native and edge-native design principles
- [Akkademy](https://akkademy.akka.io): Free training courses
- [Blog](https://akka.io/blog): Technical articles and product updates
- [Corporate Context (akka.io/llms.txt)](https://akka.io/llms.txt): Business, pricing, partnerships, and go-to-market context
```

**Generated `llms-full.txt`:** All documentation content inlined into a single Markdown file. Auto-generated from built Antora output. Sections separated by H1/H2 headings matching the page titles. No links to follow — the full content of every page is present.

## External Content (Secondary — linked from docs, not hosted in docs)

Docs should serve as pointers to all Akka technical content across akka.io, Akkademy, and external sites. External links are secondary content — clearly distinguished from primary SDK docs but discoverable alongside them. Full inventory in `docs/akka-io-content-inventory.md (in the planning workspace)`.

### Placement by Doc Section

**Why Akka**
- [What is Agentic AI?](https://akka.io/blog/what-is-agentic-ai) — foundational definition
- [What is Agentic AI? (Guide)](https://akka.io/what-is-agentic-ai) — N-tier to A-tier, 5 workflow patterns
- [Agentic AI: Why Experience Matters More Than Hype](https://akka.io/blog/agentic-ai-why-experience-matters-more-than-hype)
- [Beyond the Hype: AI Agent Framework Obstacles](https://akka.io/blog/beyond-the-hype-how-to-address-ai-agent-dev-framework-obstacles)
- [Agentic AI Frameworks: A 2026 Guide](https://akka.io/blog/agentic-ai-frameworks) — competitive landscape
- [Video: Overview of Akka Agentic Platform](https://akka.io/blog/video-akka-overview)
- [Akka Performance Benchmarks](https://akka.io/akka-performance-benchmark) — 1.4M TPS, 9ms latency, $11.77/1K TPS

**What is Akka**
- [Akka Agentic AI Platform](https://akka.io/akka-agentic-ai-platform) — platform overview
- [How Akka Works](https://akka.io/how-akka-works) — technical architecture
- [Pricing](https://akka.io/pricing) — tiers and pricing
- [Professional Services](https://akka.io/professional-services) — 8 weeks to production
- [Trust Center](https://trust.akka.io) — compliance certifications
- Application type pages (link per use case):
  - [Agentic AI](https://akka.io/app-types/agentic-ai)
  - [Event-Sourced](https://akka.io/app-types/event-sourced)
  - [Transactional](https://akka.io/app-types/transactional)
  - [Digital Twins](https://akka.io/app-types/digital-twins)
  - [Durable Execution](https://akka.io/app-types/durable-execution)
  - [Analytics](https://akka.io/app-types/analytics)
  - [Streaming](https://akka.io/app-types/streaming)
  - [Edge](https://akka.io/app-types/edge)

**Who Uses Akka**
- Customer stories linked per persona/industry vertical:
  - AI/Agentic: [Swiggy](https://akka.io/blog/2x-latency-improvement-in-swiggy-ml-and-ai-platform), [Manulife](https://akka.io/blog/manulife-selects-akka-to-operationalize-agentic-ai), [Llaama](https://akka.io/customer-stories/llaama-helps-biopharma-companies-create-ai-driven-treatments-with-akka), [MrCall](https://akka.io/customer-stories/ai-powered-call-center-mrcall-uses-akka-for-simultaneous-voip-requests), [Leap Rail](https://akka.io/customer-stories/healthcare-ai-startup-leap-rail-akka), [DeductiveAI](https://akka.io/customer-stories/deductive-ai-accelerates-rca-up-to-90-percent), [Coho AI](https://akka.io/customer-stories/coho-ai-brings-innovative-solutions-to-market-75-percent-faster)
  - Financial Services: [Capital One](https://akka.io/customer-stories/capital-one-scales-real-time-auto-loan-decisioning-with-akka), [Judopay](https://akka.io/customer-stories/judopay-builds-efficient-dependable-agile-payments-solutions-on-a-reactive-architecture), [Yields](https://akka.io/customer-stories/yields-lowers-cost-financial-model-management-building-on-akka)
  - Healthcare: [Doctolib](https://akka.io/customer-stories/1m-healthcare-providers-rely-on-akka-for-resilient-secure-messaging-via-doctolib)
  - Manufacturing/IoT: [reflek.io/Renault](https://akka.io/customer-stories/reflekio-renault-transform-global-manufacturing-akka-based-saas-digital-twin-execution-platform), [John Deere](https://akka.io/customer-stories/john-deere-improves-crop-yields-with-precision-agriculture), [CERN](https://akka.io/customer-stories/akka-helps-keep-groundbreaking-physics-experiments-running-smoothly)
  - E-Commerce: [Tubi](https://akka.io/customer-stories/personalized-user-experiences-drive-increased-advertising-revenue-at-tubi), [Walmart](https://akka.io/customer-stories/walmart-boosts-conversions-by-20-percent-with-akka)
  - Telecom: [Verizon](https://akka.io/customer-stories/verizon-wireless-deploys-akka-doubles-business-performance-results)
  - Logistics: [Cone Center](https://akka.io/customer-stories/cone-center-chooses-akka-to-build-sophisticated-logistics-applications-for-major-ports-and-distribution-centers)
  - [All Customer Stories](https://akka.io/customer-stories)

**Tutorials**
- Demos (link alongside corresponding tutorials):
  - [Demo: Build and Deploy Multi-Agent System](https://akka.io/blog/demo-build-and-deploy-a-multi-agent-system-with-akka)
  - [Demo: New Agent Component](https://akka.io/blog/demo-new-akka-sdk-component-agent)
  - [Demo: MCP Support](https://akka.io/blog/demo-mcp-support-in-akka)
  - [Demo: Temperature Monitoring Agent](https://akka.io/blog/demo-temperature-monitoring-agent)
  - [Demo: Akka code init](https://akka.io/blog/akka-code-init-demo)
  - [Demo: Simplified Workflow Step Syntax](https://akka.io/blog/demo-simplified-workflow-step-syntax)
  - [All Demos](https://akka.io/blog?tag=demo)
- [Akkademy](https://akkademy.akka.io) — free training courses (link from Getting Started)
- [Introducing Akka Specify](https://akka.io/blog/introducing-akka-specify) — SDD announcement (link from SDD tutorials)

**Developing > Components**
- Component deep-dives (link from each SDK component page):
  - Agents: [Akka Agents blog](https://akka.io/blog/akka-agents-quickly-create-agents-mcp-grpc-api), [Agents product page](https://akka.io/akka-agents), [Introducing Agent Component](https://akka.io/blog/introducing-akkas-new-agent-component)
  - Workflows: [Akka Orchestration blog](https://akka.io/blog/akka-orchestration-guide-moderate-and-control-agents), [Orchestration product page](https://akka.io/akka-orchestration), [Workflows with Akka](https://akka.io/blog/workflows-with-akka)
  - Entities/Memory: [Akka Memory blog](https://akka.io/blog/akka-memory-durable-in-memory-and-sharded-data), [Memory product page](https://akka.io/akka-memory)
  - Streaming: [Akka Streaming blog](https://akka.io/blog/akka-streaming-high-performance-stream-processing-for-real-time-ai), [Streaming product page](https://akka.io/akka-streaming)
- [MCP, A2A, ACP: What Does It All Mean?](https://akka.io/blog/mcp-a2a-acp-what-does-it-all-mean) — link from Agents/MCP docs
- [Building Real-Time Video AI with Gemini](https://akka.io/blog/building-real-time-video-ai-service-with-google-gemini) — link from Streaming AI use case

**Developing > Use Cases**
- [Agentic AI Use Cases](https://akka.io/blog/agentic-ai-use-cases) — 21 use cases (link from use cases index)
- [5 Key Capabilities for Agentic AI](https://akka.io/blog/key-capabilities-for-agentic-ai) — link from use cases overview
- [Adopting Agentic AI for Financial Services](https://akka.io/blog/adopting-agentic-ai-systems-for-financial-services-applications) — link from Enterprise Patterns
- Application type pages cross-linked per use case (see What is Akka above)

**Understanding > Agentic AI Concepts**
- [Agentic Systems Are Distributed Systems](https://akka.io/blog/agentic-systems-are-distributed-systems)
- [Event Sourcing: The Backbone of Agentic AI](https://akka.io/blog/event-sourcing-the-backbone-of-agentic-ai)
- [Demystifying AI, LLMs, and RAG](https://akka.io/blog/demystifying-ai-llms-and-rag)
- [Trustworthy AI with Akka](https://akka.io/blog/trustworthy-ai-with-akka)

**Understanding > Architecture**
- [How Akka Works](https://akka.io/how-akka-works) — unified data & logic, event-driven fabric
- [Cell-Based Architectures and Akka](https://akka.io/blog/cell-based-architectures-and-akka) — includes PDF guide
- [How Does Akka Clustering Work?](https://akka.io/blog/how-does-akka-clustering-work)
- [Backbone of Agentic AI and Distributed Systems](https://akka.io/blog/the-backbone-of-agentic-ai-distributed-systems-and-oss-sustainability)

**Understanding > Multi-Region & Resilience**
- [Multi-Region Replicated Apps](https://akka.io/blog/multi-region-replicated-apps)
- [Build and Run Apps with 99.9999% Availability](https://akka.io/blog/build-and-run-apps-with-6-9s-availability)
- [Demo: Surviving the Split](https://akka.io/blog/demo-surviving-the-split-how-akka-handles-disaster-scenarios)
- [Demo: Recovering a Destroyed Region](https://akka.io/blog/demo-recovering-a-completely-destroyed-region)

**Understanding > Governance & the Runtime**
- [Trustworthy AI with Akka](https://akka.io/blog/trustworthy-ai-with-akka)
- [Creating Certainty in the Age of Agentic AI](https://akka.io/blog/webinar-creating-certainty-in-the-age-of-agentic-ai)

**Operating > Deployment Models**
- [Automated Operations](https://akka.io/automated-operations) — AAO product page
- [New Deployment Options](https://akka.io/blog/new-akka-deployment-options-elasticity-on-any-infrastructure)
- [Akka Launches New Deployment Options](https://akka.io/blog/akka-launches-new-deployment-options-for-agentic-ai-at-scale)

**Operating > Observability**
- [Akka Performance Benchmarks](https://akka.io/akka-performance-benchmark)

**Reference**
- [Release Notes](https://doc.akka.io/reference/release-notes.html)
- [Security Announcements](https://doc.akka.io/reference/security-announcements/)
- [GitHub](https://github.com/akka)
- [Discord Community](https://discord.com/invite/QZc652rgtf)
- [Support Portal](https://support.akka.io)

### Foundational / External Resources (dedicated subsection in Reference or Understanding)

These are foundational works by Akka's leadership that inform the platform's philosophy and design. Should be prominently linked from Understanding and Reference.

- [The Reactive Manifesto](https://www.reactivemanifesto.org) — by Jonas Boner, Dave Farley, Roland Kuhn, Martin Thompson. Four pillars: Responsive, Resilient, Elastic, Message Driven. The foundational philosophy behind Akka's architecture.
- [The Reactive Principles](https://www.reactiveprinciples.org) — by Jonas Boner et al. 8 principles + 6 patterns for cloud-native and edge-native design. Companion to the Reactive Manifesto.
- **"Reactive Design Patterns"** (Manning) — by Roland Kuhn, Brian Hanafee, Jamie Allen. Patterns for building reactive systems with Akka.
- **Jonas Boner's writings on distributed architecture** — referenced in [CTO Asks CTO podcast](https://akka.io/blog/cto-asks-cto-akka-ai-agents-distributed-systems)

### Talks, Podcasts & Webinars (link contextually + collect in Reference)

- [CTO Asks CTO: Jonas Boner on Akka, AI Agents, Distributed Systems](https://akka.io/blog/cto-asks-cto-akka-ai-agents-distributed-systems)
- [TechEdge AI Talks with Tyler Jewell](https://akka.io/blog/akka-ceo-on-architecting-agentic-scalable-distributed-systems)
- [InfoQ: Building Agentic AI Systems at Scale](https://akka.io/blog/building-agentic-ai-systems-at-scale)
- [QCon London: From Concept to Code](https://akka.io/blog/from-concept-to-code-navigating-ai-services)
- [QCon London: Blueprint for Agentic AI Services](https://akka.io/blog/qcon-london-agentic-ai-services-blueprint)
- [AI Agents Are Coming for Your SaaS Stack](https://akka.io/blog/ai-agents-are-coming-for-your-saas-stack)
- Webinars:
  - [Design Patterns for Agentic AI](https://akka.io/blog/webinar-agentic-ai-design-patterns)
  - [Mastering Event Sourcing](https://akka.io/blog/webinar-mastering-event-sourcing)
  - [Blueprint for Agentic AI Services](https://akka.io/blog/webinar-blueprint-for-agentic-ai-services)
  - [Creating Certainty in the Age of Agentic AI](https://akka.io/blog/webinar-creating-certainty-in-the-age-of-agentic-ai)

### Training
- [Akkademy](https://akkademy.akka.io) — free training platform. Link from Tutorials > Getting Started and from Who Uses Akka.

## Implementation Requirements

### Review Process
- Multiple reviewers via GitHub pull request process
- This is why staged commits matter — reviewers need to be able to evaluate individual changes without wading through a monolithic diff

### Staged Commits
All changes MUST be staged as individually reviewable commits within a single PR:

1. **One logical change per commit** — Each commit should represent a single, reviewable unit of work (e.g., "add Why Akka page", "restructure nav order", "add external links to Agents component page"). A reviewer should be able to understand and evaluate each commit independently.

2. **Commit ordering** — Commits should be ordered so the docs build and render correctly at each step:
   - Structural changes first (nav reordering, new empty pages/sections)
   - Content changes next (new page content, rewrites of existing pages)
   - Cross-linking last (adding external content links, persona routing links)

3. **Commit message format** — Each commit message should state what changed and why, referencing the spec section it implements (e.g., "Add Why Akka landing page — three barriers/dimensions framing per spec").

4. **No compound commits** — Do not bundle unrelated changes. A commit that adds a new page should not also restructure an unrelated section. If a reviewer says "I approve half of this" the commit is too big.

5. **Suggested commit sequence** (approximate, adapt as needed):
   - Nav restructure (add Why Akka, What is Akka, Who Uses Akka placeholders)
   - Why Akka page content
   - What is Akka page content
   - Who Uses Akka persona routing page
   - Tutorials section reorder (SDD-first)
   - Developing section reorder (SDD top, manual setup fallback)
   - New Use Cases section (one commit per use case page, or group related stubs)
   - New Integrations section
   - Understanding section updates (new Governance & the Runtime page, Architecture reframe)
   - Operating section updates (service tier alignment, persona-aware observability)
   - Reference section updates (glossary, llms.txt)
   - External content links — one commit per doc section (e.g., "Add external links to Understanding pages")
   - Foundational resources page (Reactive Manifesto, Reactive Principles, books)

## Existing Page Audit

Full audit of all pages in `https://github.com/akka/akka-sdk/tree/main/docs`. Source has 523 pages across 7 modules.

### Summary

| Category | Count | Description |
|----------|-------|-------------|
| Keep as-is | ~185 | Mostly auto-generated CLI reference + views query syntax reference. Add metadata only. |
| Light touch-up | ~75 | Add page metadata attributes, Overview/See Also sections, voice fixes to second-person. |
| Rewrite | ~20 | SDD-first alignment, page structure template, major repositioning. |
| New | ~8 | Why Akka, What is Akka, Who Uses Akka, Resources, Governance & the Runtime, etc. |
| Merge | 1 | `concepts/concepts.adoc` into `concepts/index.adoc` |
| Delete | 0 | No pages identified for deletion. |

### High-Priority Rewrites (do first)
1. `sdk:spec-driven-development.adoc` — THE canonical SDD page. Must be the most polished page on the site.
2. `concepts:ai-agents.adoc` — Foundational AI positioning. Rewrite around three barriers.
3. `sdk:agents.adoc` — Primary component page, most-visited. SDD-first, page template.
4. `getting-started:author-your-first-service.adoc` — First-touch experience. Must lead with SDD.
5. `concepts:development-process.adoc` — Must flip to SDD-first.
6. `concepts:architecture-model.adoc` — Reframe as full-stack platform positioning.
7. `sdk:agents/guardrails.adoc` — Risk Control showcase. Governance-built-in.
8. `sdk:sanitization.adoc` — PII scrubbing as built-in governance, EU AI Act.
9. `operations:akka-platform.adoc` — AAO value prop, service tiers.
10. `reference:glossary.adoc` — Expand with AI terms, three-barriers/dimensions terminology.

### Module-Level Notes

**getting-started → Tutorials (21 pages)**
- `starthere.adoc` and `index.adoc`: Rewrite as Tutorials landing page with persona-based recommendations
- `author-your-first-service.adoc`: Rewrite to lead with SDD (currently hand-codes first)
- `spec-your-first-agent.adoc`: Already SDD-first, light touch-up
- Planner agent sub-pages (7): Light touch-up (metadata, voice, See Also)
- Ask-akka-agent sub-pages (5): Light touch-up
- Shopping cart (3): Rewrite to lead with SDD
- `samples.adoc`: Keep as-is, add metadata

**concepts → Understanding (16 pages)**
- `index.adoc`: Rewrite to frame around three dimensions
- `ai-agents.adoc`: Rewrite — foundational AI positioning
- `architecture-model.adoc`: Rewrite — full-stack platform framing
- `development-process.adoc`: Rewrite — SDD-first
- `distributed-systems.adoc`: Rewrite — connect to Production Gap barrier
- `acls.adoc`: Rewrite — reframe around Risk Control
- Remaining 10 pages: Light touch-up (metadata, Overview/See Also)
- Note: `concepts/concepts.adoc` should merge into `index.adoc`
- Bug: `xref:declarative-effects.adoc[]` missing `concepts:` module prefix in nav

**sdk → Developing (41 pages)**
- `spec-driven-development.adoc`: Rewrite — canonical SDD page, highest priority
- `sdk/index.adoc`, `components/index.adoc`: Rewrite with SDD framing and decision matrix
- Component pages (agents, ESE, KVE, workflows, endpoints, views, consumers, timed-actions): Rewrite for page template compliance (Overview, SDD path, Testing, See Also) and heading standardization. Note: these are reference-style — structural changes only, not educational rewrites.
- `agents/guardrails.adoc`: Rewrite — Risk Control showcase
- `sanitization.adoc`: Rewrite — governance-built-in showcase
- Agent sub-pages (11): Light touch-up
- Integrations sub-pages (5): Light touch-up
- Setup & config pages (9): Light touch-up

**operations → Operating (44 pages)**
- `index.adoc`: Rewrite to frame around Operator personas and Reliability dimension
- `akka-platform.adoc`: Rewrite — AAO positioning with service tiers
- Remaining ~42 pages: Light touch-up (metadata, voice) or keep as-is. Broker config pages (5) are vendor-specific and fine as-is.

**reference → Reference (~200 pages)**
- `glossary.adoc`: Rewrite — expand significantly with AI/SDD/governance terms
- Auto-generated CLI reference (~150 pages): Keep as-is, no manual changes
- Views query syntax (~30 pages): Keep as-is, pure reference
- Descriptors, config, JWT, OIDC pages: Keep as-is, add metadata
- `support.adoc`: Move to Resources section

### New Pages Needed
1. Why Akka landing page
2. What is Akka landing page
3. Who Uses Akka persona routing page
4. Resources landing page
5. Governance & the Runtime (Understanding)
6. Service Tiers overview (What is Akka or Operating)
7. Use Case pages (10, some as stubs — see Use Cases section)
8. Tutorials landing page (or rewrite of `starthere.adoc`)

## Status
- [x] Analyzed akka.ai/llms.txt positioning
- [x] Studied existing doc.akka.io structure and content
- [x] Analyzed competitor docs (Temporal, n8n, LangChain, CrewAI, Google ADK)
- [x] Full proposed outline agreed upon
- [x] Updated spec with revised positioning from llms.txt (2026-03-25)
- [x] Inventoried all akka.io technical content and mapped to doc sections (2026-03-25)
- [x] Added external content placement plan (blog, demos, customer stories, product pages)
- [x] Added foundational resources (Reactive Manifesto, Reactive Principles, Jonas' book)
- [x] Added staged commit implementation requirements
- [x] Define writing standards and guidelines (2026-03-25)
- [x] Existing page audit completed (2026-03-25)
- [ ] Draft specific pages
