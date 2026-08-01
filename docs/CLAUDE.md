# Writing Akka SDK documentation

Guidance for writing and editing the pages under `docs/src`. Merged from the writing standards in
[docs-overhaul-spec.md](docs-overhaul-spec.md) (the docs-overhaul project plan, which takes
precedence where the two met) and field experience writing the governance and autonomous-agents
pages. The spec holds the migration plan: positioning, target structure, page audit, and the
llms.txt generator design. This file holds the ongoing standards.

## Audience: people and AI agents

The primary consumer is an AI agent as much as a human. The docs are published as HTML for people
and as markdown plus `llms.txt` for AI coding assistants (see the README). Structure, label, and
write content so agents can parse, navigate, and act on it — but the same literal, consistent,
self-contained style serves both audiences, so you do not write differently for them:

- Use one term for one thing, everywhere. Consistent naming is what keeps the docs retrievable and
  quotable out of context, and greppable when an API changes.
- Give exact, current API names and signatures, not paraphrases.
- Keep examples self-contained and compiled, so code copied out of a page actually runs.
- Cross-reference with `xref:`, not "see above" or "as described earlier" — position does not
  survive extraction. Name the thing: "the `OrderEntity` class defined in
  xref:sdk:event-sourced-entities.adoc#modeling-state[Modeling State]".
- Headings serve as an API for the page: an agent reading only the heading tree should understand
  the page's structure and jump to the right section.

Do not stuff keywords or over-explain for the machine; that hurts both audiences.

## Voice and tone

- Second person only. The reader is "you"; Akka is "Akka", never "we".
- Imperative for instructions ("Annotate the class with `@Component`."), declarative for facts
  ("Events are delivered at least once.").
- Medium formality: technical and precise, not academic. No contractions in body text. No humor,
  colloquialisms, or filler.
- Short sentences. Break compound-complex sentences into two. Lead with the action or fact, not
  the condition. Prefer several short sentences over one long sentence stitched together with
  em-dashes.
- Define jargon on first use. Every domain term (passivation, sharding, Effects, CloudEvents) is
  defined or linked to the glossary on the page where it first appears.
- Say what a thing is and how to use it. Do not sell it. No marketing or spin — drop "seamless",
  "powerful", "robust", "simply", and value claims. Describe behaviour and trade-offs instead.
- Positioning content (the three barriers and three dimensions, differentiator claims, competitive
  comparisons) belongs on the Why Akka / What is Akka / Who Uses Akka pages and the llms.txt
  preamble, per the overhaul spec. Component and reference pages stay at behaviour level and link
  to those pages rather than repeating the claims.

## Avoid LLM tells

These patterns read as machine-generated. Don't use them:

- The bolded-noun + italic-verb list (“**Guardrails** *enforce*, **Classifiers** *score* …”).
  Write plain sentences.
- Cutesy concept labels in backticks (`` `how` `` / `` `when/what` ``). Explain the idea directly.
- Exhaustive enumerations for completeness ("a regex, embeddings, a traditional ML model, a small
  specialised model, a prompted or fine-tuned LLM, or an external API"). Give two or three examples.
- Design-pattern sermons ("keeps the decision logic separate from the scoring logic, and lets you
  re-point it at deployment time"). Just say what the code does.
- Stock phrases repeated across pages ("primary consumer", the same tricolon). Vary them or cut them.
- Slash compounds in prose ("scoring/labeling", "and/or"). Write it out.
- The "humans forget, the runtime doesn't" trope, restated on every page.

## Write for a global audience

Many readers are not native English speakers. Keep the language literal.

- No idioms or figurative phrasal verbs: "fair game", "sit in front of", "reach for", "hands off",
  "steer", "drives", "point it at", "spin up", "under the hood".
- Use the plain verb instead: intercept, use, influence, configure, delegate, run.
- If a phrase needs cultural knowledge to parse, replace it.

## Page structure

Component and feature pages follow the template from the overhaul spec:

- **H1 in noun form.** "Agents", "Workflows" — not "Implementing Agents".
- **An extractable definition** as the first sentence after the title: what the component is, in
  one sentence. An agent reading only the first paragraph should know what the page covers.
- **Overview** — what it does, when to use it, when not to use it (with links to the
  alternatives), and how it relates to other components. State which components this one commonly
  pairs with and xref them; don't rely on the reader having read the other pages.
- **SDD path** — on pages that have one, before the manual material (see below).
- **Core sections** — vary by component, but use the same section names as sibling pages
  ("Modeling state" on every entity page, not "Defining state" on one and "State model" on
  another).
- **Testing** and **See Also** — required on every component and feature page. Keep "See Also" to
  pages that are actually relevant.

Headings below H1 must be self-descriptive without page context — "Compensating failed workflow
steps", not "Compensation" — because headings are extracted into search results and agent indexes.

For anything the template does not cover, match the structure and voice of the neighbouring pages.

## Page metadata

Every page declares `:page-*:` attributes after the `include::ROOT:partial$include.adoc[]` line.
They feed the Antora UI and the llms.txt generator:

```asciidoc
:page-component-type: agent
:page-summary: An Agent interacts with an AI model to perform a specific task, maintaining session memory and supporting multi-agent collaboration.
:page-when-to-use: LLM-backed tasks, conversational AI, multi-agent orchestration
:page-related: sdk:workflows.adoc, sdk:event-sourced-entities.adoc, concepts:ai-agents.adoc
:page-prerequisites: Java 21, Akka SDK basics
:page-persona: builder-developer, builder-ai-ml
```

The `:page-summary:` says what the page's subject does, not why it is good.

## SDD-first, manual fallback

On pages that cover both Spec-Driven Development and the manual approach, SDD is the primary
content path and occupies the main body. The manual approach goes in a labeled admonition:

```asciidoc
[TIP.manual]
.Without SDD
====
To create this component manually without SDD, ...
====
```

Never present manual-first with SDD as the aside. For SDD installation, the AI-marketplace plugin
is the primary path and the Akka CLI (`akka specify init`) is the fallback, also in an admonition.

## Reflect the SDK as it is

- Document what is in the SDK now, not planned or half-merged features. Keep forward-looking notes
  out unless explicitly asked, and mark them clearly when included.
- Use the team's current term for a thing. For example it is "the ledger", not "the interaction log"
  or "the interaction ledger".
- Don't invent conceptual comparisons the API doesn't make (a classifier-vs-guardrail philosophy,
  for instance). Describe each construct on its own terms.

## Keep docs in step with the code

Docs drift when an API changes and the prose or examples don't follow. Don't build machinery to
track drift — build so it surfaces itself:

- Compiled snippets are the main defence. When an API changes, the snippet stops compiling and the
  build forces the fix. This is a big part of why examples belong in snippets, not inline.
- Prose has no compiler, so keep it greppable: one term for one thing. When you change an API in the
  code, grep the docs for the type, method, and config-key names and update the prose and snippets
  in the same change.
- Treat a visit to any page as a chance to reconcile it against the current code, not just to make
  the one edit you came for. If you find drift you can't fix now, flag it rather than leave it
  silently wrong.
- Don't keep a separate "docs status" changelog. It rots faster than the docs. The build plus
  reconcile-on-touch is enough.

### Check the API docs too

These pages link into the generated javadoc/scaladoc (`{attachmentsdir}/api/...`), so the narrative
and the API docs have to agree. Whenever you work here:

- Read the type's javadoc/scaladoc alongside the page and fix whichever one is wrong.
- Check the code actually has adequate API docs: public types and methods, and a `package-info.java`
  for each package. If an API you're documenting has thin or missing doc comments, add them or flag it.
- Keep those doc comments at contract level — what the caller needs — not maintainer internals.
  Maintainer notes belong in `//` comments or the PR.

## Code lives in compiled snippets

No hand-written code in the `.adoc`. Every example is real source in `samples/`, pulled in by tag,
so it compiles and can't drift from the API.

```
[source,java,indent=0]
.{sample-base-url}/doc-snippets/src/main/java/com/example/x/Foo.java[Foo.java]
----
include::example$doc-snippets/src/main/java/com/example/x/Foo.java[tag=bar]
----
```

with `// tag::bar[]` / `// end::bar[]` around the region in `Foo.java`.

- Code before explanation: show the block first, then explain with numbered `<1>` `<2>` callouts
  below it, per the existing convention.
- The first example on a page includes the essential imports (use a separate tag for the import
  block if needed).
- Keep examples under 30 lines where possible. When an example spans multiple classes, link to the
  complete working sample with the `{sample-base-url}` attribute.
- Test-scope examples go in the sample's `src/test/java`; the `include::` path points there.
  Snippet-only classes that compile but never run are fine (`*Sample` naming in `doc-snippets`;
  `demo/docs/*Sample` in the playground).
- The doc-snippets `pom.xml` pins the released SDK version (`3.6.0`). To compile snippets against
  unreleased APIs, bump the parent version locally (by hand or with `updateSdkVersions.sh`),
  compile, then revert — or, for samples wired into the sbt build (for example
  `sample-autonomous-agent-playground`), compile directly against the in-repo SDK with
  `sbt <project>/Test/compile`. Never commit the snapshot version.
- Run `docs/bin/verify-include-paths.sh` to check the display label and the include path agree.

> The overhaul spec also asks for a one-line comment at the top of each tagged region stating what
> it demonstrates. Existing snippets do not do this; the status quo stands pending team discussion.

## Content deduplication

- Use Antora partials for content that appears on multiple pages (`sdk/partials/entity-sharding.adoc`
  and friends), included via `include::partial$...[]`.
- Shared concepts (sharding, passivation, multi-region replication, delivery guarantees) live in
  the Understanding pages. On a component page, give a one- or two-sentence summary with an xref to
  the full explanation; don't reproduce the section.

## External links

Every external URL is an Antora attribute defined in the central registry,
`docs/src/modules/ROOT/partials/external-links.adoc`, included on every page through
`include::ROOT:partial$include.adoc[]`. Never write a literal external URL in a page. Add missing
URLs to the registry following its naming convention, then reference the attribute:

```asciidoc
See the link:{url-blog-agents}[Akka Agents deep-dive, window="new"] for architecture details.
```

## Preview locally

Prerequisites (Docker) and the full build process are in `docs/README.md`. In short, from the repo
root:

- Full site, with working API links: `make managed local`. `managed` also builds the javadoc, so it
  is slower.
- Quick content preview: `make local`. Skips the javadoc and version attributes, so API links and
  `{akka-javasdk-version}` won't resolve, but the prose, structure, and snippet includes render.

Both write to `target/site/`; open `target/site/index.html` (or `make open`).

- If the javadoc step fails with "illegal inheritance from sealed trait", that is stale incremental
  build state — run `sbt akka-javasdk/clean akka-javasdk-testkit/clean` and retry.
- `docs/bin/verify-include-paths.sh` checks that include labels and paths agree, without a build.

After a non-trivial docs change, build the preview and offer to open it for the user, so they can
sanity-check the rendered pages.

## Checklist

1. Page follows the structure template: noun-form H1, extractable first-sentence definition,
   Overview, Testing, See Also; headings self-descriptive; section names match sibling pages.
2. `:page-*:` metadata attributes are present and the `:page-summary:` says what it does, not why
   it is good.
3. Lead with what it is and the smallest working example; put configuration and edge cases after.
4. SDD content first where the page has an SDD path; manual approach in a `[TIP.manual]` admonition.
5. Every code block is a tagged include from a compiled source — never inline code — explained
   with numbered callouts.
6. Cross-link with `xref:`; external URLs only via the link registry; keep "See also" relevant.
7. Re-read the diff for contractions, idioms, and the LLM tells above before committing.
8. Build a local preview (see [Preview locally](#preview-locally)) and offer to open it for the user.
9. Reconcile the page against the type's javadoc/scaladoc, and confirm the code has adequate API
   docs (public types, methods, and `package-info`).
10. Keep glossary entries alphabetical and their terms consistent with the code.
