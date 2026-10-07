import { readFile, writeFile } from 'node:fs/promises';
import { text } from 'node:stream/consumers';
import { JSDOM } from 'jsdom';
import { convertHtmlToMarkdown } from 'dom-to-semantic-markdown';

// NUL-separated paths preserve spaces and newlines in filenames from find.
const filenames = (await text(process.stdin)).split('\0').filter(Boolean);
if (filenames.length === 0) {
  throw new Error('No HTML files selected for Markdown conversion');
}
const dom = new JSDOM('');
const parser = new dom.window.DOMParser();

console.log(`Converting ${filenames.length} HTML pages to Markdown`);
for (const filename of filenames) {
  try {
    const html = await readFile(filename, 'utf8');
    const markdown = convertHtmlToMarkdown(html, {
      overrideDOMParser: parser,
      extractMainContent: true,
    });
    await writeFile(`${filename}.md`, markdown);
  } catch (error) {
    throw new Error(`Failed to convert ${filename}`, { cause: error });
  }
}
console.log(`Converted ${filenames.length} HTML pages to Markdown`);
