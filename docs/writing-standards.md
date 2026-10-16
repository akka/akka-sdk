# Akka SDK Documentation — Writing Standards

These standards govern all documentation prose. Vale enforces the automatable subset
(`docs/styles/Akka/`); reviewers enforce the rest. They apply to net-new and rewritten
content. The one carve-out is the **About Akka** section, which presents the Akka Agentic AI
Platform and its four solutions, and may describe capabilities that have no task page yet. It
still follows the voice rules below and quotes no marketing figures.

## Voice and tone
- **Second person.** The reader is "you"; the product is "Akka". Drop "we".
- **Imperative for instructions, declarative for facts.** "Annotate the class." / "Events are delivered at least once."
- **Short sentences.** Break compound-complex sentences in two. Lead with the action or fact.
- **Medium formality.** Technical and precise, not academic. No contractions in body text, no humor or filler.
- **Use full words in prose and headings.** Write "documentation", "environment", and
  "evaluation" instead of "docs", "env", and "eval". Preserve literal paths, commands, API names,
  and other identifiers. Established technical terms are acceptable when defined or linked.
- **State the behavior or criterion directly.** Avoid conversational idioms and vague judgments:
  use "meets the acceptance criteria" instead of "meets the bar", "is released" instead of
  "ships", and "quality falls below the required threshold" instead of "it slips". Name the
  relevant criterion rather than referring to "what good means". Keep replacements short and
  concrete; do not replace informal language with unnecessarily formal wording.
- **Define jargon on first use.** Link domain terms (passivation, sharding, Effects) to the glossary on first appearance.

## Banned in documentation prose
Positioning/marketing language does not belong in the documentation (it may inform *what* to
document, never *how it reads*):
- The three-barriers / three-dimensions narrative, benefit-pillar copy, superlatives, competitor jabs.
- Marketing words (Vale-flagged): seamless, supercharge, game-changer, blazing fast, turnkey,
  best-in-class, cutting-edge, revolutionize, effortless.
- Rhetorical questions, dramatic em-dashes, "isn't just X — it's Y".
- **Structural AI tells** (reviewer-enforced): stacked em-dash asides ("X — the thing that Y — …"), rule-of-three lists ("build, run, and govern"; "faster, simpler, cheaper"), and uniform rhythm (every paragraph 3–4 sentences of similar length). Vary sentence and paragraph length on purpose; prefer a plain list or a reworded sentence over a set of three; use the em-dash sparingly.
- **Governance:** assert a checkable/enforced corpus — never quote counts of regulations, controls, or certifications.
- **Service tiers:** not documented.
- **No `_internal` / "Dojo" content.**

## Terminology
- **"Akka Specify"** = the solution: spec-driven development tooling. Use it for the solution as a whole.
- **"Akka Specify Plugin"** = the plugin installed in an AI harness. Use it for the plugin itself.
- **Solution names** (Akka SDK, Akka Specify, Akka Verify, Akka Optimize) and **"Akka Agentic AI Platform"** appear in the About Akka section. Other sections are named by task and do not frame their pages by solution.
- **"spec-driven development"** = the practice; the documentation teaches this via the Plugin.

## Page structure
Every component/feature page has, at minimum: **Overview** (one-sentence definition, when to use,
when not to, relationships), **Testing** (where applicable), and **See Also**.
- **H1 noun form:** "Agents", not "Implementing agents".
- **H2s self-descriptive:** they must make sense pulled into a search result ("Compensating failed workflow steps", not "Compensation").
- **Code before explanation,** with numbered callouts; all code pulled from compilable samples via `include::example$…` — never fabricated inline.

## AI-first metadata
Set `:page-summary:`, `:page-when-to-use:`, `:page-related:`, `:page-persona:` on each page.
Put an extractable definition in the first sentence after the title.

## Links
All external URLs go through attributes in `ROOT/partials/external-links.adoc` — no inline external URLs (Vale-flagged).
