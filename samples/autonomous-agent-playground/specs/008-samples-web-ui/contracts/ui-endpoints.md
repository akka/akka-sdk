# Contract — UI surface (HTML / static assets)

**Endpoint class**: `demo.ui.api.PlaygroundUiEndpoint`
**ACL**: `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))` at the class level (matches the existing per-sample endpoints).

This endpoint serves the browser-facing assets and provides the SPA-fallback so client-side routes resolve.

---

## Routes

### `GET /`

Returns a 302 redirect to `/playground`. Convenience landing for `localhost:9000/`.

- **Request**: empty
- **Response**: `302 Found`, `Location: /playground`

### `GET /playground/static/**`

Serves any asset under `src/main/resources/static-resources/playground/static/`.

- **Request path**: e.g. `/playground/static/styles/akka.css`, `/playground/static/app.js`, `/playground/static/samples/research.js`
- **Implementation**: takes `HttpRequest` parameter and returns `HttpResponses.staticResource(request, "/playground/static/")`.
- **Response**: 200 with the asset and inferred MIME type, or `HttpResponses.notFound()` if the asset is not on the classpath.
- **Caching**: default static-resource caching headers from the SDK; no manual override.

### `GET /playground/**`

Catch-all that returns the same `index.html` so the client-side router takes over for paths like `/playground`, `/playground/research`, `/playground/research/run/<runId>`.

- **Implementation**: `return HttpResponses.staticResource("playground/index.html");` — note the *single-file* form, not the prefix form, so any deep URL maps to the same shell.
- **Order**: in the endpoint class this method **must** be declared *after* the `/playground/static/**` route so a real asset path is not shadowed. Akka SDK route matching is most-specific-first; the explicit `/static/**` prefix wins, but we still order in source for human readability.
- **Response**: 200 `text/html`. The browser then loads `app.js` which inspects `location.pathname` and routes to the right view.

### Test contract

`PlaygroundUiEndpointIntegrationTest` (uses `TestKitSupport` + `httpClient`):

1. `GET /` returns 302 to `/playground`.
2. `GET /playground` returns 200 with content type `text/html` and a body containing `<title>Autonomous Agent Playground</title>` and a `<script src="/playground/static/app.js"…>` reference.
3. `GET /playground/static/app.js` returns 200 with content type `application/javascript`.
4. `GET /playground/static/styles/akka.css` returns 200 with content type `text/css`.
5. `GET /playground/static/does-not-exist.js` returns 404.
6. `GET /playground/research` returns 200 with the same body as `/playground` (SPA fallback).
7. `GET /playground/research/run/abc-123` returns 200 with the same body as `/playground` (SPA fallback). The "abc-123" run does not exist; the **client** is responsible for the 404 page (see `run-control.md` for the API-level 404).

---

## Static asset layout (informative, not part of HTTP contract)

```text
src/main/resources/static-resources/playground/
├── index.html
└── static/
    ├── app.js
    ├── theme.js
    ├── event-log.js
    ├── run-summary.js
    ├── styles/
    │   ├── akka.css       # verbatim from akka-context/ui/default-akka-style.css
    │   └── playground.css # extension stylesheet (theme override + UI primitives)
    └── samples/
        ├── _registry.js
        ├── helloworld.js
        ├── pipeline.js
        ├── docreview.js
        ├── dynamic.js
        ├── research.js
        ├── consulting.js
        ├── support.js
        ├── publishing.js
        ├── debate.js
        ├── negotiation.js
        ├── peerreview.js
        └── devteam.js
```
