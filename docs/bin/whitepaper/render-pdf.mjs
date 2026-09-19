// Render the AAO technical overview hub-and-spoke pages into branded "white paper" PDFs.
// Each download is composed by reader intent from one or more built HTML pages:
//   - the hub download is the complete overview (every model and cloud),
//   - a model getting-started download is that whole model (shared setup, every cloud, data flows),
//   - a hyperscaler download is the actionable runbook for one model and cloud (shared setup,
//     that cloud, data flows),
//   - the data-flows download is the flows on their own.
// A part may keep only selected sections, so the shared BYOK8s requirements appear once in a
// combined PDF instead of repeating inside every self-contained BYOK8s cloud page.
// Usage: node render-pdf.mjs [site-dir]
import { chromium } from 'playwright';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { PDFDocument } from 'pdf-lib';

const siteDir = resolve(process.argv[2] || 'target/site');
const outDir = resolve(siteDir, 'operations/_attachments/whitepapers');
const cssPath = resolve(dirname(new URL(import.meta.url).pathname), 'print.css');
const printCss = readFileSync(cssPath, 'utf8');

const base = 'operations/technical-overview';
const HUB = `${base}.html`;
const BYOC = `${base}/byoc.html`;
const AWS = `${base}/aws.html`;
const AZURE = `${base}/azure.html`;
const GCP = `${base}/gcp.html`;
const BYOK8S_REQ = `${base}/byok8s-requirements.html`;
const BYOK8S_AWS = `${base}/byok8s-aws.html`;
const BYOK8S_AZURE = `${base}/byok8s-azure.html`;
const BYOK8S_GCP = `${base}/byok8s-gcp.html`;
const DATA_FLOWS = `${base}/data-flows.html`;

// Section ids of the shared BYOK8s requirements partial. Dropping these from a self-contained
// BYOK8s cloud page leaves only that page's cloud-specific sections for a combined PDF.
const REQ_SECTIONS = ['_installation_flow', 'requirements', '_capacity_planning', '_egress_allowlist_and_teleport', 'smoke-tests'];
const specifics = (rel, id) => ({ rel, keep: [id] });

// Each download: an ordered list of parts merged into one PDF. The first part carries the cover.
const DOWNLOADS = [
  {
    out: 'aao-technical-overview-full.pdf',
    title: 'Technical Overview',
    subtitle: 'The complete overview: architecture, operations, and every deployment model and cloud.',
    parts: [{ rel: HUB }, { rel: BYOC }, { rel: AWS }, { rel: AZURE }, { rel: GCP }, { rel: BYOK8S_REQ },
      specifics(BYOK8S_AWS, '_aws_specifics'), specifics(BYOK8S_AZURE, '_azure_specifics'), specifics(BYOK8S_GCP, '_gcp_specifics'),
      { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byoc.pdf',
    title: 'BYOC',
    subtitle: 'Bring Your Own Cloud: shared setup, AWS, Azure, GCP, and data flows.',
    parts: [{ rel: BYOC }, { rel: AWS }, { rel: AZURE }, { rel: GCP }, { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byoc-aws.pdf',
    title: 'BYOC on AWS',
    subtitle: 'Shared BYOC setup, AWS specifics, and data flows.',
    parts: [{ rel: BYOC }, { rel: AWS }, { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byoc-azure.pdf',
    title: 'BYOC on Azure',
    subtitle: 'Shared BYOC setup, Azure specifics, and data flows.',
    parts: [{ rel: BYOC }, { rel: AZURE }, { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byoc-gcp.pdf',
    title: 'BYOC on GCP',
    subtitle: 'Shared BYOC setup, GCP specifics, and data flows.',
    parts: [{ rel: BYOC }, { rel: GCP }, { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byok8s.pdf',
    title: 'BYOK8s',
    subtitle: 'Bring Your Own Kubernetes: shared requirements, AWS, Azure, GCP, and data flows.',
    parts: [{ rel: BYOK8S_REQ }, specifics(BYOK8S_AWS, '_aws_specifics'), specifics(BYOK8S_AZURE, '_azure_specifics'),
      specifics(BYOK8S_GCP, '_gcp_specifics'), { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byok8s-aws.pdf',
    title: 'BYOK8s on AWS',
    subtitle: 'Self-contained BYOK8s setup for AWS, with data flows.',
    parts: [{ rel: BYOK8S_AWS }, { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byok8s-azure.pdf',
    title: 'BYOK8s on Azure',
    subtitle: 'Self-contained BYOK8s setup for Azure, with data flows.',
    parts: [{ rel: BYOK8S_AZURE }, { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-byok8s-gcp.pdf',
    title: 'BYOK8s on GCP',
    subtitle: 'Self-contained BYOK8s setup for GCP, with data flows.',
    parts: [{ rel: BYOK8S_GCP }, { rel: DATA_FLOWS }],
  },
  {
    out: 'aao-data-flows.pdf',
    title: 'Data Flows',
    subtitle: 'Every network flow between your environment and the Akka platform.',
    parts: [{ rel: DATA_FLOWS }],
  },
];
const EYEBROW = 'Akka Automated Operations';

mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch();

// Render one part to a PDF buffer. Results are cached by content so a page reused across
// downloads (data flows, the shared setup pages) renders once.
const cache = new Map();
async function renderPart({ rel, keep, cover, footerTitle }) {
  const key = JSON.stringify({ rel, keep: keep || null, cover: cover || null, footerTitle });
  if (cache.has(key)) return cache.get(key);

  const page = await browser.newPage();
  await page.goto(pathToFileURL(resolve(siteDir, rel)).href, { waitUntil: 'networkidle', timeout: 60000 });
  await page.evaluate(({ eyebrow, cover, keep, reqSections }) => {
    // Drop any "download as PDF" note; it is pointless inside the PDF itself.
    document.querySelectorAll('a[href*="/whitepapers/"]').forEach(a => {
      (a.closest('.admonitionblock') || a.closest('.paragraph') || a).remove();
    });
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
    // self-contained BYOK8s cloud page so it does not repeat in a combined PDF.
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
  }, { eyebrow: EYEBROW, cover: cover || null, keep: keep || null, reqSections: REQ_SECTIONS });

  await page.addStyleTag({ content: printCss });
  await page.emulateMedia({ media: 'print' });
  await page.evaluate(() => document.fonts.ready);

  const buf = await page.pdf({
    format: 'A4',
    printBackground: true,
    margin: { top: '16mm', bottom: '18mm', left: '16mm', right: '16mm' },
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate:
      '<div style="width:100%;font-family:\'Roboto Mono\',monospace;font-size:7pt;color:#8a8a8a;padding:0 16mm;display:flex;justify-content:space-between;">' +
      '<span>Akka Automated Operations · ' + footerTitle + '</span>' +
      '<span class="pageNumber"></span>/<span class="totalPages"></span></div>',
  });
  await page.close();
  cache.set(key, buf);
  return buf;
}

for (const d of DOWNLOADS) {
  const merged = await PDFDocument.create();
  for (let i = 0; i < d.parts.length; i++) {
    const part = d.parts[i];
    const buf = await renderPart({
      rel: part.rel,
      keep: part.keep,
      cover: i === 0 ? { title: d.title, subtitle: d.subtitle } : null,
      footerTitle: d.title,
    });
    const src = await PDFDocument.load(buf);
    const copied = await merged.copyPages(src, src.getPageIndices());
    copied.forEach(pg => merged.addPage(pg));
  }
  writeFileSync(resolve(outDir, d.out), await merged.save());
  console.log('wrote', resolve(outDir, d.out));
}

await browser.close();
