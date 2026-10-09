import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';

const output = new URL('../../target/core-browser-tests/', import.meta.url);
// Fail loudly if the actual Scala.js test application has not been linked.
await readFile(new URL('main.js', output));
const editorOutput = new URL('../../target/editor-browser-tests/', import.meta.url);
const editorHtml = `<!doctype html><html><head><style>.scalajs-ui-editor__surface {min-height: 6rem} .ember-toolbar button {margin: 2px}</style></head><body><div id="root"></div><script type="module">
import { editorFixtures } from '/editor/main.js';
window.fixtures = editorFixtures;
</script></body></html>`;
const html = `<!doctype html><html><body><div id="root"></div><script type="module">
import { coreFixtures } from '/main.js';
window.fixtures = coreFixtures;
</script></body></html>`;
const server = createServer(async (req, res) => {
  const path = new URL(req.url, 'http://127.0.0.1').pathname;
  if (path === '/submit' && req.method === 'POST') {
    let body = '';
    for await (const chunk of req) body += chunk;
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.end(body);
    return;
  }
  if (path === '/editor') {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(editorHtml);
    return;
  }
  if (path === '/') {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(html);
    return;
  }
  const editorAsset = path.startsWith('/editor/');
  const assetPath = editorAsset ? path.slice('/editor'.length) : path;
  if (!/^\/[A-Za-z0-9_.-]+\.js$/.test(assetPath)) {
    res.writeHead(404).end();
    return;
  }
  try {
    res.setHeader('Content-Type', 'text/javascript');
    res.end(await readFile(new URL(assetPath.slice(1), editorAsset ? editorOutput : output)));
  } catch { res.writeHead(404).end(); }
});
server.listen(4187, '127.0.0.1');
