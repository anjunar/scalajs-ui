import express from "express";
import { access } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { siteConfig } from "./site-config.mjs";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
// Serve the built site under the same path GitHub Pages uses (site.config.json).
const sitePath = siteConfig.basePath;
const pages = resolve(root, "dist/pages");
await access(resolve(pages, "index.html"));
const app = express();
app.use((req, res, next) => {
  if ("nojs" in req.query) res.set("Content-Security-Policy", "script-src 'none'");
  next();
});
if (sitePath !== "") app.get("/", (_req, res) => res.redirect(`${sitePath}/`));
app.use(sitePath || "/", express.static(pages));
// GitHub Pages answers unknown paths with the site's 404.html; do the same here.
app.use((_req, res) => res.status(404).sendFile(resolve(pages, "404.html")));
const port = Number(process.env.PORT ?? 3320);
app.listen(port, "127.0.0.1", () => console.log(`http://127.0.0.1:${port}${sitePath}/`));
