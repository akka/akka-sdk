// Helpers used by per-sample descriptors. Intentionally tiny — no abstraction beyond what every
// descriptor genuinely needs: HTML escaping and a couple of result renderers for common shapes.

export function escapeHtml(s) {
  if (s == null) return '';
  return String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => {
    if (k === 'className') node.className = v;
    else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2).toLowerCase(), v);
    else node.setAttribute(k, v);
  });
  for (const child of [].concat(children)) {
    if (child == null) continue;
    if (typeof child === 'string') node.appendChild(document.createTextNode(child));
    else node.appendChild(child);
  }
  return node;
}

export async function postJson(path, body) {
  const resp = await fetch(path, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!resp.ok) throw new Error(`POST ${path} failed: ${resp.status}`);
  // Some endpoints (e.g. publishing's /approve, /reject) return plain text rather than JSON.
  // Don't blindly call resp.json() — sniff the content-type so callers that don't need a
  // typed body still see a successful return.
  const ct = resp.headers.get('content-type') ?? '';
  if (ct.includes('application/json')) return resp.json();
  return resp.text();
}

export async function getJson(path) {
  const resp = await fetch(path);
  if (!resp.ok) throw new Error(`GET ${path} failed: ${resp.status}`);
  return resp.json();
}

// Generic field-list renderer for structured results. Pass an array of [label, valueText].
export function renderFields(pairs) {
  const dl = el('dl', { className: 'result-fields' });
  for (const [label, value] of pairs) {
    if (value == null || value === '') continue;
    dl.appendChild(el('dt', {}, label));
    dl.appendChild(el('dd', {}, String(value)));
  }
  return dl;
}

export function renderList(items) {
  const ul = el('ul');
  for (const item of items || []) ul.appendChild(el('li', {}, String(item)));
  return ul;
}

/**
 * Render a string of (potentially) Markdown as an HTMLElement. LLM-generated content typically
 * arrives with `#` headings, `*` lists, fenced code blocks, etc. — rendering it as plain text
 * leaves the markup visible. Uses the {@code marked} library loaded from CDN in index.html.
 * Falls back to a plain {@code <pre>} block if {@code marked} isn't available (e.g. CDN
 * blocked or offline).
 */
export function renderMarkdown(text) {
  const wrap = document.createElement('div');
  wrap.className = 'markdown';
  if (text == null || text === '') return wrap;
  if (window.marked && typeof window.marked.parse === 'function') {
    wrap.innerHTML = window.marked.parse(String(text));
  } else {
    const pre = document.createElement('pre');
    pre.textContent = String(text);
    wrap.appendChild(pre);
  }
  return wrap;
}

/**
 * For lists of bullet-phrase strings (e.g. keyFindings). Renders each item with
 * {@link renderMarkdown} so inline formatting (bold, code, links) works inside the bullets,
 * while the bullets themselves are normal {@code <ul><li>…</li></ul>} markup.
 */
export function renderMarkdownList(items) {
  const ul = el('ul', { className: 'markdown-list' });
  for (const item of items || []) {
    const li = el('li');
    li.appendChild(renderMarkdown(item));
    ul.appendChild(li);
  }
  return ul;
}
