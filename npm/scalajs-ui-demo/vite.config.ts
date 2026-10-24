import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { uiCode } from "./tools/vite-plugin-ui-code.js";

const clientEntry = fileURLToPath(new URL("./src/entry-client.ts", import.meta.url));
const stylesheetEntry = fileURLToPath(new URL("./src/styles/style.css", import.meta.url));

function viteBasePath(value: string | undefined): string {
  if (!value || value === "/") return "/";
  const withLeadingSlash = value.startsWith("/") ? value : `/${value}`;
  return `${withLeadingSlash.replace(/\/+$/, "")}/`;
}

// npm workspaces hoist every package in npm/* into the repo root's node_modules,
// so `@anjunar/scalajs-ui-core` resolves through a symlink into the source tree. Vite's
// default server.fs.allow stops at the workspace root it auto-detects, which
// would refuse to serve real paths outside npm/scalajs-ui-demo/. The repo root's own
// vite.config.js opens the same door for the same reason.
const monorepoRoot = fileURLToPath(new URL("../..", import.meta.url));

// Every package of the family this app depends on, read off package.json so a
// dependency added there is deduplicated here without a second edit.
const packageJson = JSON.parse(
  readFileSync(new URL("./package.json", import.meta.url), "utf8")
) as { dependencies?: Record<string, string> };
const uiPackages = Object.keys(packageJson.dependencies ?? {}).filter((name) =>
  name.startsWith("@anjunar/scalajs-ui")
);

export default defineConfig(({ isSsrBuild }) => ({
  base: viteBasePath(process.env.UI_BASE_PATH),
  plugins: [tailwindcss(), uiCode()],
  resolve: {
    // The one-runtime invariant, enforced at the bundler.
    //
    // `runtime.ts` holds the installed runtime in a single module-level
    // variable. Two *module instances* of scalajs-ui-core are two such slots, and the
    // second one has never seen installRuntime() -- the failure reads as
    // "No UI runtime installed" with the call visibly right above it
    // (JAVASCRIPT_API.md §13). Vite's SSR module runner produced exactly that
    // when the same file was reached once through a `file:` symlink and once
    // through its real path.
    //
    // dedupe forces both paths onto one instance. It is the reason the entry
    // points may import by package specifier again; without it the old relative
    // import would still be load-bearing. Same argument for the bridge: two
    // copies of the linked Scala.js bundle would be two component trees. And
    // the same again, more quietly, for scalajs-ui-forms and scalajs-ui-json: both keep
    // decorator metadata in module-level WeakMaps (`validators.ts`, `json.ts`),
    // so a second instance of either would simply not know the fields the first
    // one decorated. The list is therefore every @anjunar/scalajs-ui* package this app
    // depends on, not a hand-picked subset; scripts/verify-single-runtime.mjs
    // counts core, forms and json in the emitted bundles.
    dedupe: uiPackages,
  },
  server: {
    fs: {
      allow: [monorepoRoot],
    },
  },
  build: {
    sourcemap: true,
    // No index.html to infer an entry from any more -- the document is
    // rendered entirely by src/app/document.ts. The manifest is how
    // server.mjs's clientAssets() finds the client build's hashed file names
    // in production (mirrors the repo root's vite.config.js).
    manifest: !isSsrBuild,
    // Keep CSS independent from hydration. The server can therefore link the
    // stylesheet for a JavaScript-free SSR response, while the manifest still
    // owns the hashed production file name.
    ...(isSsrBuild
      ? {}
      : { rollupOptions: { input: { main: clientEntry, styles: stylesheetEntry } } }),
  },
}));
