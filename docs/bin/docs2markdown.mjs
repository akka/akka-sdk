import { readFile, writeFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { convertHtmlToMarkdown } from 'dom-to-semantic-markdown';

// NUL-separated paths preserve spaces and newlines in filenames from find.
process.stdin.setEncoding('utf8');
let input = '';
for await (const chunk of process.stdin) input += chunk;
const filenames = input.split('\0').filter(Boolean);
const dom = new JSDOM('');
const parser = new dom.window.DOMParser();

try {
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
} finally {
  dom.window.close();
}
