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
