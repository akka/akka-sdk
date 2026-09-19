// Render the AAO technical overview hub-and-spoke pages into branded "white paper" PDFs:
// one PDF per page, plus a merged full-overview PDF (hub followed by every spoke).
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

// The hub first, then the spokes. The merged PDF concatenates in this order.
const PAGES = [
  { rel: 'operations/technical-overview.html',                     out: 'aao-technical-overview.pdf',   title: 'Technical Overview',   subtitle: 'Architecture, installation, and operations across Kubernetes on AWS, Azure, and GCP.' },
  { rel: 'operations/technical-overview/aws.html',                 out: 'aao-aws.pdf',                  title: 'On AWS',               subtitle: 'Resources, identity model, connectivity, and BYOK8s notes for AWS.' },
  { rel: 'operations/technical-overview/azure.html',               out: 'aao-azure.pdf',                title: 'On Azure',             subtitle: 'Resources, identity model, connectivity, and BYOK8s notes for Azure.' },
  { rel: 'operations/technical-overview/gcp.html',                 out: 'aao-gcp.pdf',                  title: 'On GCP',               subtitle: 'Resources, identity model, connectivity, and BYOK8s notes for GCP.' },
  { rel: 'operations/technical-overview/byok8s-requirements.html', out: 'aao-byok8s-requirements.pdf',  title: 'BYOK8s Requirements',  subtitle: 'Infrastructure requirements for running AAO on a Kubernetes cluster you provide.' },
  { rel: 'operations/technical-overview/byok8s-aws.html',          out: 'aao-byok8s-aws.pdf',           title: 'BYOK8s on AWS',        subtitle: 'Self-contained BYOK8s setup for AWS.',   full: false },
  { rel: 'operations/technical-overview/byok8s-azure.html',        out: 'aao-byok8s-azure.pdf',         title: 'BYOK8s on Azure',      subtitle: 'Self-contained BYOK8s setup for Azure.', full: false },
  { rel: 'operations/technical-overview/byok8s-gcp.html',          out: 'aao-byok8s-gcp.pdf',           title: 'BYOK8s on GCP',        subtitle: 'Self-contained BYOK8s setup for GCP.',   full: false },
  { rel: 'operations/technical-overview/data-flows.html',          out: 'aao-data-flows.pdf',           title: 'Data Flows',           subtitle: 'Every network flow between your environment and the Akka platform.' },
];
const FULL_OUT = 'aao-technical-overview-full.pdf';
const EYEBROW = 'Akka Automated Operations';

mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch();
const buffers = [];

for (const p of PAGES) {
  const page = await browser.newPage();
  await page.goto(pathToFileURL(resolve(siteDir, p.rel)).href, { waitUntil: 'networkidle', timeout: 60000 });
  await page.evaluate(({ eyebrow, title, subtitle }) => {
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
    const doc = document.querySelector('article.doc') || document.querySelector('.doc');
    if (doc) {
      const cover = document.createElement('div');
      cover.className = 'wp-cover';
      const el = (cls, text) => {
        const d = document.createElement('div');
        d.className = cls;
        if (text != null) d.textContent = text;
        return d;
      };
      cover.append(
        el('wp-brandbar'),
        el('wp-eyebrow', eyebrow),
        el('wp-title', title),
        el('wp-sub', subtitle),
        el('wp-meta', 'doc.akka.io · Akka'),
      );
      doc.insertBefore(cover, doc.firstChild);
    }
  }, { eyebrow: EYEBROW, title: p.title, subtitle: p.subtitle });

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
      '<span>Akka Automated Operations &mdash; ' + p.title + '</span>' +
      '<span class="pageNumber"></span>/<span class="totalPages"></span></div>',
  });
  writeFileSync(resolve(outDir, p.out), buf);
  if (p.full !== false) buffers.push(buf);
  await page.close();
  console.log('wrote', resolve(outDir, p.out));
}

// Merge every page PDF into one full white paper, hub first.
const merged = await PDFDocument.create();
for (const buf of buffers) {
  const src = await PDFDocument.load(buf);
  const copied = await merged.copyPages(src, src.getPageIndices());
  copied.forEach(pg => merged.addPage(pg));
}
writeFileSync(resolve(outDir, FULL_OUT), await merged.save());
console.log('wrote', resolve(outDir, FULL_OUT));

await browser.close();
