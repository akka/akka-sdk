# Akka SDK Documentation — Writing Standards

These standards govern all documentation prose. Vale enforces the automatable subset
(`docs/styles/Akka/`); reviewers enforce the rest. They apply to net-new and rewritten
content. The one carve-out is the **About Akka capability pages** duplicated from
marketing, which may keep positioning voice (see the execution plan).

## Voice and tone
- **Second person.** The reader is "you"; the product is "Akka". Drop "we".
- **Imperative for instructions, declarative for facts.** "Annotate the class." / "Events are delivered at least once."
- **Short sentences.** Break compound-complex sentences in two. Lead with the action or fact.
- **Medium formality.** Technical and precise, not academic. No contractions in body text, no humor or filler.
- **Define jargon on first use.** Link domain terms (passivation, sharding, Effects) to the glossary on first appearance.

## Banned in documentation prose
Positioning/marketing language does not belong in the docs (it may inform *what* to
document, never *how it reads*):
- The three-barriers / three-dimensions narrative, benefit-pillar copy, superlatives, competitor jabs.
- Marketing words (Vale-flagged): seamless, supercharge, game-changer, blazing fast, turnkey,
  best-in-class, cutting-edge, revolutionize, effortless.
- Rhetorical questions, dramatic em-dashes, "isn't just X — it's Y".
- **Governance:** assert a checkable/enforced corpus — never quote counts of regulations, controls, or certifications.
- **Service tiers:** not documented.
- **No `_internal` / "Dojo" content.**

## Terminology
- **"Akka Specify Plugin"** = the command-line / marketplace tool the docs describe. Use this.
- **"Akka Specify"** (bare) = the marketing delivery methodology. **Never appears in the docs** (Vale-flagged).
- **"spec-driven development"** = the practice; docs teach this via the Plugin.

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
