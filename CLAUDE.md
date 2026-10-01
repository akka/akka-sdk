Use the instructions in @AGENTS.md.

## Writing style

Applies to READMEs, docs/, javadoc, comments, pom comments, CI and mise comments,
application.conf comments, and git commit comments.

- Simple language. Short sentences. No contractions. No em-dash.
- Second person. Imperative for instructions, declarative for facts. No first person.
- Active voice with a named actor. Do not promote the receiver to subject ("the agent is sent
  the input"); say who does what ("the runner sends the input to the agent"). Do not defer the
  noun ("X is what ...", "what X does is ..."); name it first.
- No mannered prose and metaphors. E.g. use "this point still matters" instead of "this point earns its keep".
- Describe current behaviour. No development history, no rejected alternatives, no counts that
  go stale (how many tests, modules or classes). Do not document planned or half-built features.
  `docs/architecture-breakdown.md` is the exception.
- No marketing words and no idioms ("reach for", "spin up", "under the hood", "point it at",
  "load-bearing"). Avoid "seam", "corpus", "substrate"; say interface, dataset, runtime.
- Do not document the obvious. Do not explain Akka SDK or library behaviour unless it is
  critical to a gotcha in this code.
- Javadoc is contract level: what the caller needs. Maintainer notes go in `//` comments next
  to the code or in the PR description.

## PRs and commits
- One PR should target one feature or one improvement, don't expand with unrelated work outside the original plan
- PR branches can be stacked to keep each PR focused and easier to review
- Conventional prefix: `fix`/`chore`/`docs`/`test`/`build`/`bump`.
- Short commit message, state the purpose, don't restate the diff.
- No `Co-Authored-By: Claude` or `Claude-Session`
