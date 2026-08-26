// Render a built Antora page into a branded "white paper" PDF.
// Usage: node render-pdf.mjs <site-dir> <page-rel-path> <out-pdf> [--png]
// Defaults target the AAO technical overview.
import { chromium } from 'playwright';
import { readFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const siteDir  = resolve(process.argv[2] || 'target/site');
const pageRel  = process.argv[3] || 'operations/technical-overview.html';
const outPdf   = resolve(process.argv[4] || 'target/site/operations/_attachments/whitepapers/aao-technical-overview.pdf');
const wantPng  = process.argv.includes('--png');
const cssPath  = resolve(dirname(new URL(import.meta.url).pathname), 'print.css');

const EYEBROW  = process.env.WP_EYEBROW  || 'Akka Automated Operations';
const TITLE    = process.env.WP_TITLE    || 'Technical Overview';
const SUBTITLE = process.env.WP_SUBTITLE || 'Architecture, installation, and operations across Kubernetes on AWS, Azure, and GCP.';

const pageUrl = pathToFileURL(resolve(siteDir, pageRel)).href;
const printCss = readFileSync(cssPath, 'utf8');

const browser = await chromium.launch();
const page = await browser.newPage();
await page.goto(pageUrl, { waitUntil: 'networkidle', timeout: 60000 });

// Flatten tabs: reveal every panel and label it with its tab name; build a cover.
await page.evaluate(({ eyebrow, title, subtitle }) => {
  document.querySelectorAll('.tabpanel').forEach(panel => {
    panel.removeAttribute('hidden');
    const labId = panel.getAttribute('aria-labelledby');
    const tab = labId && document.getElementById(labId);
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
    // Build with textContent (never innerHTML) so cover text is treated as
    // data, not markup — no injection sink even for build-supplied values.
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
}, { eyebrow: EYEBROW, title: TITLE, subtitle: SUBTITLE });

await page.addStyleTag({ content: printCss });
await page.emulateMedia({ media: 'print' });
await page.evaluate(() => document.fonts.ready);

mkdirSync(dirname(outPdf), { recursive: true });
if (wantPng) {
  await page.screenshot({ path: outPdf.replace(/\.pdf$/, '.png'), fullPage: true });
}
await page.pdf({
  path: outPdf,
  format: 'A4',
  printBackground: true,
  margin: { top: '16mm', bottom: '18mm', left: '16mm', right: '16mm' },
  displayHeaderFooter: true,
  headerTemplate: '<span></span>',
  footerTemplate:
    '<div style="width:100%;font-family:\'Roboto Mono\',monospace;font-size:7pt;color:#8a8a8a;padding:0 16mm;display:flex;justify-content:space-between;">' +
    '<span>Akka Automated Operations &mdash; Technical Overview</span>' +
    '<span class="pageNumber"></span>/<span class="totalPages"></span></div>',
});
await browser.close();
console.log('wrote', outPdf);
