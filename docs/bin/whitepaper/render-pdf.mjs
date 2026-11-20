// Render the AAO technical overview pages into branded "white paper" PDFs.
// Each download is composed by reader intent from one or more built HTML pages:
//   - the full download is the complete overview (every model and cloud),
//   - a route download is the actionable runbook for one model and cloud (shared setup, that
//     cloud, data flows).
// A part may keep only selected sections, so the shared BYOK8s requirements appear once in the
// full PDF instead of repeating inside every self-contained BYOK8s cloud page.
//
// Pages that use the `dense` layout (full-screen grids) are wide. They do not go into the A4
// downloads. They go into one shared wide PDF, and each A4 download points to it.
//
// After the parts are rendered, the script merges them, drops blank pages, stamps the footer and
// continuous page numbers, and writes bookmarks from the headings.
//
// Usage: node render-pdf.mjs [site-dir]
// Set WHITEPAPERS_STRICT=1 to fail when a technical overview page is in no download.
import { chromium } from 'playwright';
import { readFileSync, readdirSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { PDFDocument, PDFName, PDFArray, PDFDict, PDFNumber, PDFHexString, StandardFonts, rgb } from 'pdf-lib';

const siteDir = resolve(process.argv[2] || 'target/site');
const outDir = resolve(siteDir, 'operations/_attachments/whitepapers');
const cssPath = resolve(dirname(new URL(import.meta.url).pathname), 'print.css');
const printCss = readFileSync(cssPath, 'utf8');
const STRICT = process.env.WHITEPAPERS_STRICT === '1';

const base = 'deployment/technical-overview';
const HUB = `${base}.html`;
const BYOC = `${base}/byoc.html`;
const AWS = `${base}/byoc-aws.html`;
const AZURE = `${base}/byoc-azure.html`;
const GCP = `${base}/byoc-gcp.html`;
const BYOK8S_AWS = `${base}/byok8s-aws.html`;
const BYOK8S_AZURE = `${base}/byok8s-azure.html`;
const BYOK8S_GCP = `${base}/byok8s-gcp.html`;
const BYOK8S_REQ = `${base}/byok8s-requirements.html`;
const DATA_FLOWS = `${base}/data-flows.html`;
const DATA_FLOWS_GRID = `${base}/data-flows-grid.html`;
const DATA_FLOWS_OPTIMIZE_GRID = `${base}/data-flows-optimize-grid.html`;
const OPTIMIZE_DEPENDENCIES = `${base}/optimize-dependencies.html`;

const WIDE_PDF = 'aao-data-flows.pdf';
const WIDE_TITLE = 'Data Flows';
const EYEBROW = 'Akka Automated Operations';

// Section ids of the shared BYOK8s requirements partial. Dropping these from a self-contained
// BYOK8s cloud page leaves only that page's cloud-specific sections for the full PDF.
const REQ_SECTIONS = ['_installation_flow', 'requirements', '_capacity_planning', '_egress_allowlist_and_teleport', 'smoke-tests'];

// The data flows page and its grids. The grids are wide, so the A4 PDFs carry only the first.
const dataFlowsParts = [DATA_FLOWS, DATA_FLOWS_GRID, DATA_FLOWS_OPTIMIZE_GRID];

// Each download: an ordered list of parts. A part is a page path, or { rel, keep } to keep only
// some sections. The first part carries the cover.
const DOWNLOADS = [
  {
    out: 'aao-technical-overview-full.pdf',
    title: 'Technical Overview',
    subtitle: 'The complete overview: architecture, operations, and every deployment model and cloud.',
    parts: [HUB, `${base}/serverless.html`, `${base}/dedicated.html`, `${base}/optimize.html`, BYOC, AWS, AZURE, GCP, BYOK8S_REQ,
      { rel: BYOK8S_AWS, keep: ['_aws_specifics'] },
      { rel: BYOK8S_AZURE, keep: ['_azure_specifics'] },
      { rel: BYOK8S_GCP, keep: ['_gcp_specifics'] },
      ...dataFlowsParts, OPTIMIZE_DEPENDENCIES],
  },
  {
    out: 'aao-byoc.pdf',
    title: 'BYOC',
    subtitle: 'Bring Your Own Cloud: shared setup, AWS, Azure, GCP, and data flows.',
    parts: [BYOC, AWS, AZURE, GCP, ...dataFlowsParts],
  },
  {
    out: 'aao-byok8s.pdf',
    title: 'BYOK8s',
    subtitle: 'Bring Your Own Kubernetes: shared requirements, AWS, Azure, GCP, and data flows.',
    parts: [BYOK8S_REQ, { rel: BYOK8S_AWS, keep: ['_aws_specifics'] },
      { rel: BYOK8S_AZURE, keep: ['_azure_specifics'] },
      { rel: BYOK8S_GCP, keep: ['_gcp_specifics'] }, ...dataFlowsParts],
  },
  {
    out: 'aao-byoc-aws.pdf',
    title: 'BYOC on AWS',
    subtitle: 'Shared BYOC setup, AWS specifics, and data flows.',
    parts: [BYOC, AWS, ...dataFlowsParts],
  },
  {
    out: 'aao-byoc-azure.pdf',
    title: 'BYOC on Azure',
    subtitle: 'Shared BYOC setup, Azure specifics, and data flows.',
    parts: [BYOC, AZURE, ...dataFlowsParts],
  },
  {
    out: 'aao-byoc-gcp.pdf',
    title: 'BYOC on GCP',
    subtitle: 'Shared BYOC setup, GCP specifics, and data flows.',
    parts: [BYOC, GCP, ...dataFlowsParts],
  },
  {
    out: 'aao-byok8s-aws.pdf',
    title: 'BYOK8s on AWS',
    subtitle: 'Self-contained BYOK8s setup for AWS, with data flows.',
    parts: [BYOK8S_AWS, ...dataFlowsParts],
  },
  {
    out: 'aao-byok8s-azure.pdf',
    title: 'BYOK8s on Azure',
    subtitle: 'Self-contained BYOK8s setup for Azure, with data flows.',
    parts: [BYOK8S_AZURE, ...dataFlowsParts],
  },
  {
    out: 'aao-byok8s-gcp.pdf',
    title: 'BYOK8s on GCP',
    subtitle: 'Self-contained BYOK8s setup for GCP, with data flows.',
    parts: [BYOK8S_GCP, ...dataFlowsParts],
  },
];

// Technical overview pages that no PDF includes, on purpose.
// The Azure setup page already includes these diagrams; full-screen copies would duplicate them.
const NOT_IN_PDF = new Set([
  `${base}/byoc-azure-default-image.html`,
  `${base}/byoc-azure-vnet-peering-image.html`,
]);

const asPart = p => (typeof p === 'string' ? { rel: p } : p);

// Fail or warn when a page under the technical overview is in no download, so a new page
// is added to a PDF or excluded on purpose.
function checkCoverage() {
  const dir = resolve(siteDir, base);
  const pages = [HUB, ...readdirSync(dir).filter(f => f.endsWith('.html')).map(f => `${base}/${f}`)];
  const used = new Set(DOWNLOADS.flatMap(d => d.parts.map(p => asPart(p).rel)));
  const missing = pages.filter(rel => !used.has(rel) && !NOT_IN_PDF.has(rel));
  if (missing.length) {
    const msg = `These pages are in no PDF. Add them to DOWNLOADS or NOT_IN_PDF in render-pdf.mjs:\n  ${missing.join('\n  ')}`;
    if (STRICT) { console.error(msg); process.exit(1); }
    console.warn(msg);
  }
}

mkdirSync(outDir, { recursive: true });
checkCoverage();
const browser = await chromium.launch();

// Render one part to a PDF buffer. Results are cached by content so a page reused across
// downloads (data flows, the shared setup pages) renders once.
const cache = new Map();
async function renderPart({ rel, keep, cover }) {
  const key = JSON.stringify({ rel, keep: keep || null, cover: cover || null });
  if (cache.has(key)) return cache.get(key);

  const page = await browser.newPage();
  await page.goto(pathToFileURL(resolve(siteDir, rel)).href, { waitUntil: 'networkidle', timeout: 60000 });
  const wide = await page.evaluate(() => !!document.querySelector('.dense-page'));
  const heads = await page.evaluate(({ eyebrow, cover, keep, wide, wideNote }) => {
    // Links to a PDF and to the full-screen grid are redundant in a PDF. Where a grid link was,
    // say where the grid is.
    const links = [...document.querySelectorAll('a[href*="/whitepapers/"], a.open-grid')];
    const containers = links.map(a => a.closest('.admonitionblock') || a.closest('.paragraph') || a);
    if (links.some(a => a.classList.contains('open-grid'))) {
      const note = document.createElement('p');
      note.textContent = wideNote;
      containers[0].before(note);
    }
    containers.forEach(c => c.remove());
    if (wide) document.body.classList.add('wp-wide-table');
    // Flatten tabs: reveal every panel and label it with its tab name.
    document.querySelectorAll('.tabpanel').forEach(panel => {
      panel.removeAttribute('hidden');
      const tab = panel.getAttribute('aria-labelledby') && document.getElementById(panel.getAttribute('aria-labelledby'));
      const name = tab ? tab.textContent.trim() : null;
      if (name) {
        const h = document.createElement('div');
        h.className = 'wp-tab-label';
        h.textContent = name;
        panel.insertBefore(h, panel.firstChild);
      }
    });
    // Keep only the requested sections. Used to strip the shared requirements out of a
    // self-contained BYOK8s cloud page so it does not repeat in the full PDF.
    if (keep) {
      document.querySelectorAll('#preamble').forEach(e => e.remove());
      document.querySelectorAll('.sect1').forEach(s => {
        const h = s.querySelector('h2, h3');
        if (!h || !keep.includes(h.id)) s.remove();
      });
    }
    const doc = document.querySelector('article.doc') || document.querySelector('.doc');
    if (doc && cover) {
      const el = (cls, text) => {
        const d = document.createElement('div');
        d.className = cls;
        if (text != null) d.textContent = text;
        return d;
      };
      const c = el('wp-cover');
      c.append(
        el('wp-brandbar'),
        el('wp-eyebrow', eyebrow),
        el('wp-title', cover.title),
        el('wp-sub', cover.subtitle),
        el('wp-meta', 'doc.akka.io · Akka'),
      );
      doc.insertBefore(c, doc.firstChild);
    }
    // Mark each heading so its page can be found in the PDF. Chromium writes a destination for
    // every link target, so an invisible link to each marker makes it write one per heading.
    const heads = [];
    document.querySelectorAll('article.doc h2, article.doc h3').forEach(h => {
      if (h.closest('.wp-cover')) return;
      const n = heads.length;
      const mark = document.createElement('span');
      mark.id = `wp-h-${n}`;
      mark.style.cssText = 'display:block;height:0;width:0;';
      h.before(mark);
      heads.push({ n, level: h.tagName === 'H2' ? 2 : 3, text: h.textContent.trim() });
    });
    if (heads.length) {
      const box = document.createElement('div');
      box.style.cssText = 'position:absolute;left:0;top:0;width:2px;height:2px;overflow:hidden;opacity:0.01;';
      heads.forEach(h => {
        const a = document.createElement('a');
        a.href = `#wp-h-${h.n}`;
        a.style.cssText = 'display:block;width:1px;height:1px;';
        box.append(a);
      });
      document.body.prepend(box);
    }
    return heads;
  }, { eyebrow: EYEBROW, cover: cover || null, keep: keep || null, wide,
       wideNote: `The full data flows tables are in the separate wide PDF, ${WIDE_PDF}.` });

  await page.addStyleTag({ content: printCss });
  await page.emulateMedia({ media: 'print' });
  await page.evaluate(() => document.fonts.ready);
  const title = (await page.title()).split(' :: ')[0].trim();

  // The footer and page numbers are stamped after the merge, so they run through the whole PDF.
  const buf = await page.pdf({
    ...(wide ? { width: '480mm', height: '297mm' } : { format: 'A4' }),
    printBackground: true,
    margin: { top: '16mm', bottom: '18mm', left: '16mm', right: '16mm' },
  });
  await page.close();
  const result = { buf, wide, title, heads };
  cache.set(key, result);
  return result;
}

// A page with no drawing operations is blank. The footer is not part of a part's PDF, so any
// page left with almost no content is a layout artifact.
const BLANK_BYTES = 120;
function isBlank(pdfPage) {
  const contents = pdfPage.node.Contents();
  if (!contents) return true;
  const streams = contents instanceof PDFArray ? contents.asArray().map(r => pdfPage.doc.context.lookup(r)) : [contents];
  const size = streams.reduce((n, s) => n + (s && s.getContentsSize ? s.getContentsSize() : 0), 0);
  return size < BLANK_BYTES;
}

// Build the bookmarks of one part: the part title with its headings below it. The heading pages
// come from the destinations Chromium wrote for the heading markers.
function partOutline(src, r, map, offset) {
  const dests = src.catalog.lookup(PDFName.of('Dests'));
  const pageIndex = new Map(src.getPages().map((p, i) => [p.ref.toString(), i]));
  const top = { title: r.title, page: offset, y: null, children: [] };
  let lastH2 = null;
  for (const h of r.heads) {
    const dest = dests && dests.lookup(PDFName.of(`wp-h-${h.n}`));
    if (!(dest instanceof PDFArray)) continue;
    const srcPage = pageIndex.get(dest.get(0).toString());
    const yNumber = dest.lookup(3);
    const item = {
      title: h.text,
      page: map.has(srcPage) ? map.get(srcPage) : offset,
      y: map.has(srcPage) && yNumber && yNumber.asNumber ? yNumber.asNumber() : null,
      children: [],
    };
    if (h.level === 2 || !lastH2) {
      top.children.push(item);
      if (h.level === 2) lastH2 = item;
    } else {
      lastH2.children.push(item);
    }
  }
  return top;
}

// Remove the invisible links that were added to find the heading pages.
function removeMarkerLinks(src) {
  for (const pg of src.getPages()) {
    const annots = pg.node.Annots();
    if (!annots) continue;
    const kept = annots.asArray().filter(ref => {
      const dest = src.context.lookup(ref, PDFDict).get(PDFName.of('Dest'));
      return !(dest && dest.toString().startsWith('/wp-h-'));
    });
    pg.node.set(PDFName.of('Annots'), src.context.obj(kept));
  }
}

// Write the bookmark tree into the merged PDF and ask viewers to show it.
function writeOutline(doc, items) {
  if (!items.length) return;
  const ctx = doc.context;
  const pages = doc.getPages();
  const rootRef = ctx.nextRef();
  const build = (list, parentRef) => {
    const refs = list.map(() => ctx.nextRef());
    let count = list.length;
    list.forEach((it, i) => {
      const page = pages[Math.min(Math.max(it.page, 0), pages.length - 1)];
      const entries = {
        Title: PDFHexString.fromText(it.title),
        Parent: parentRef,
        Dest: ctx.obj([page.ref, PDFName.of('XYZ'), PDFNumber.of(0), PDFNumber.of(it.y == null ? page.getHeight() : it.y), PDFNumber.of(0)]),
      };
      if (i > 0) entries.Prev = refs[i - 1];
      if (i < list.length - 1) entries.Next = refs[i + 1];
      if (it.children.length) {
        const child = build(it.children, refs[i]);
        entries.First = child.first;
        entries.Last = child.last;
        entries.Count = PDFNumber.of(child.count);
        count += child.count;
      }
      ctx.assign(refs[i], ctx.obj(entries));
    });
    return { first: refs[0], last: refs[refs.length - 1], count };
  };
  const top = build(items, rootRef);
  ctx.assign(rootRef, ctx.obj({ Type: 'Outlines', First: top.first, Last: top.last, Count: PDFNumber.of(top.count) }));
  doc.catalog.set(PDFName.of('Outlines'), rootRef);
  doc.catalog.set(PDFName.of('PageMode'), PDFName.of('UseOutlines'));
}

// Merge rendered parts into one PDF with continuous page numbers and bookmarks.
async function assemble(results, { footerTitle, hasCover, title }) {
  const merged = await PDFDocument.create();
  const outline = [];
  for (const r of results) {
    const src = await PDFDocument.load(r.buf);
    removeMarkerLinks(src);
    const kept = src.getPageIndices().filter(i => !isBlank(src.getPage(i)));
    const dropped = src.getPageCount() - kept.length;
    if (dropped) console.log(`  dropped ${dropped} blank page(s) from ${r.title}`);
    const offset = merged.getPageCount();
    const copied = await merged.copyPages(src, kept);
    copied.forEach(pg => merged.addPage(pg));
    const map = new Map(kept.map((srcIndex, k) => [srcIndex, offset + k]));
    outline.push(partOutline(src, r, map, offset));
  }

  const font = await merged.embedFont(StandardFonts.Courier);
  const size = 7;
  const color = rgb(0.54, 0.54, 0.54);
  const marginX = 16 * 72 / 25.4;
  const baseline = 9 * 72 / 25.4;
  const total = merged.getPageCount();
  merged.getPages().forEach((pg, i) => {
    if (hasCover && i === 0) return;
    const { width } = pg.getSize();
    const label = `${i + 1} / ${total}`;
    pg.drawText(`${EYEBROW} · ${footerTitle}`, { x: marginX, y: baseline, size, font, color });
    pg.drawText(label, { x: width - marginX - font.widthOfTextAtSize(label, size), y: baseline, size, font, color });
  });

  writeOutline(merged, outline);
  merged.setTitle(`${title} | ${EYEBROW}`);
  merged.setAuthor('Akka');
  merged.setSubject('Akka Automated Operations');
  return merged.save();
}

// Render every download. A part that uses the dense layout goes to the shared wide PDF.
const widePartResults = new Map();
for (const d of DOWNLOADS) {
  const results = [];
  for (let i = 0; i < d.parts.length; i++) {
    const part = asPart(d.parts[i]);
    const result = await renderPart({ ...part, cover: i === 0 ? { title: d.title, subtitle: d.subtitle } : null });
    if (result.wide) {
      if (i === 0) throw new Error(`${part.rel} is wide and cannot be the first part of ${d.out}`);
      widePartResults.set(part.rel, result);
    } else {
      results.push(result);
    }
  }
  writeFileSync(resolve(outDir, d.out), await assemble(results, { footerTitle: d.title, hasCover: true, title: d.title }));
  console.log('wrote', resolve(outDir, d.out));
}

if (widePartResults.size) {
  writeFileSync(resolve(outDir, WIDE_PDF), await assemble([...widePartResults.values()], { footerTitle: WIDE_TITLE, hasCover: false, title: WIDE_TITLE }));
  console.log('wrote', resolve(outDir, WIDE_PDF));
}

await browser.close();
