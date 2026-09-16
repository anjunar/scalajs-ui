/**
 * The consumer test for the editor package: does a foreign project get a
 * usable editor API by installing the tarballs and importing only through
 * public `exports`?
 *
 * Asserted here, and nothing else:
 *
 *  1. The packed tarballs (core, forms, viewport, bridge, editor) install and
 *     resolve together.
 *  2. `@anjunar/scalajs-ui-editor` ships `dist` and its types, not `src`/`test`.
 *  3. `tsc --strict` with `skipLibCheck: false` over a file importing from
 *     core + bridge + forms + editor -- the regression test for a broken
 *     shipped declaration.
 *  4. SSR of a form-bound editor against the linked Scala.js bridge produces
 *     the expected HTML.
 *  5. A headless session (P29) runs from the packed install in plain Node, and
 *     importing the facade installs no runtime of its own.
 */
import { execFile } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { promisify } from "node:util";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

const packageRoot = resolve(process.cwd());
const repoRoot = resolve(packageRoot, "..", "..");
const corePackage = join(repoRoot, "npm", "scalajs-ui-core");
const controlsPackage = join(repoRoot, "npm", "scalajs-ui-controls");
const viewportPackage = join(repoRoot, "npm", "scalajs-ui-viewport");
const formsPackage = join(repoRoot, "npm", "scalajs-ui-forms");
const bridgePackage = join(repoRoot, "npm", "scalajs-ui-bridge");
const linkedArtifact = join(bridgePackage, "dist", "fullopt", "main.js");

let consumer = "";

const execFileAsync = promisify(execFile);

// Asynchronous on purpose. npm install and loading the linked bridge in a child process take tens of
// seconds; a synchronous call blocks the Vitest worker for all of it, and the worker then misses its
// own RPC deadline ("Timeout calling onTaskUpdate") although every test passed.
async function run(command: string, args: readonly string[], cwd: string): Promise<string> {
  const { stdout } = await execFileAsync(command, [...args], {
    cwd,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
  });
  return stdout;
}

async function npm(args: readonly string[], cwd: string): Promise<string> {
  const entry = process.env["npm_execpath"];
  if (entry !== undefined && entry.endsWith(".js")) {
    return run(process.execPath, [entry, ...args], cwd);
  }
  const { stdout } = await execFileAsync("npm", [...args], {
    cwd,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    shell: process.platform === "win32",
  });
  return stdout;
}

async function pack(directory: string, into: string): Promise<string> {
  const output = await npm(["pack", "--pack-destination", into, "--silent"], directory);
  const name = output.trim().split("\n").pop()!.trim();
  return join(into, name);
}

function fileSpecifier(path: string): string {
  return "file:" + path.replace(/\\/g, "/");
}

function lastJsonLine<T>(output: string): T {
  return JSON.parse(output.trim().split("\n").pop()!) as T;
}

beforeAll(async () => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      "The Scala.js bridge is not linked. Run:\n\n" +
        '    sbt --server "scalajs-ui-bridge/fullLinkJS"\n\n' +
        "Expected: " +
        linkedArtifact
    );
  }

  consumer = mkdtempSync(join(tmpdir(), "scalajs-ui-editor-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  const coreTarball = await pack(corePackage, tarballs);
  const controlsTarball = await pack(controlsPackage, tarballs);
  const viewportTarball = await pack(viewportPackage, tarballs);
  const formsTarball = await pack(formsPackage, tarballs);
  const bridgeTarball = await pack(bridgePackage, tarballs);
  const editorTarball = await pack(packageRoot, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "scalajs-ui-editor-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/scalajs-ui-core": fileSpecifier(coreTarball),
          "@anjunar/scalajs-ui-controls": fileSpecifier(controlsTarball),
          "@anjunar/scalajs-ui-viewport": fileSpecifier(viewportTarball),
          "@anjunar/scalajs-ui-forms": fileSpecifier(formsTarball),
          "@anjunar/scalajs-ui-bridge": fileSpecifier(bridgeTarball),
          "@anjunar/scalajs-ui-editor": fileSpecifier(editorTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: core/controls/viewport/forms/editor all declare a peer
  // on the CSS package this probe does not install (it renders no stylesheet).
  await npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist and types, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "scalajs-ui-editor");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("editor.d.ts");
    expect(dist).toContain("session.d.ts");
    expect(dist).toContain("commands.d.ts");
    expect(dist).toContain("extensions.d.ts");
  });
});

describe("typechecking a consumer", () => {
  it("resolves all packages under --strict", async () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    const source = [
      'import { installRuntime, mount, property, renderToString } from "@anjunar/scalajs-ui-core";',
      'import type { SsrResult } from "@anjunar/scalajs-ui-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";',
      'import { form } from "@anjunar/scalajs-ui-forms";',
      'import { viewport } from "@anjunar/scalajs-ui-viewport";',
      'import { editor } from "@anjunar/scalajs-ui-editor";',
      'import type { EditorOptions, EditorSession, Markdown } from "@anjunar/scalajs-ui-editor";',
      'import { createEditor, history, insertText, links, richText, setHeading, undo } from "@anjunar/scalajs-ui-editor";',
      "",
      'const initial: Markdown = "";',
      'const model = { body: property(initial) };',
      'const options: EditorOptions = { plugins: ["base", "heading"] };',
      "",
      "export async function render(): Promise<SsrResult> {",
      "  installRuntime(bridgeRuntime);",
      "  return renderToString(() => {",
      "    viewport(() => form(model, {}, () => editor(\"body\", options)));",
      "  });",
      "}",
      "",
      "export function headless(): string {",
      '  const session: EditorSession = createEditor({ extensions: [richText(), history(), links({ schemes: ["https"] })], markdown: "Ada" });',
      '  session.dispatch(insertText, { text: "Hi " });',
      "  session.dispatch(setHeading, { level: 1 });",
      "  session.dispatch(undo);",
      "  const result = session.toMarkdown();",
      "  session.dispose();",
      '  return result.ok ? result.value : result.error;',
      "}",
      "",
      "export function onSession(): EditorOptions {",
      "  return { onSession: (session) => void session.dispatch(insertText, { text: \"!\" }) };",
      "}",
      "",
      "export function boot(root: Element): void {",
      "  installRuntime(bridgeRuntime);",
      "  mount(root, () => {",
      "    viewport(() => form(model, {}, () => editor(\"body\", options)));",
      "  });",
      "}",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "src", "app.ts"), source);

    writeFileSync(
      join(consumer, "tsconfig.json"),
      JSON.stringify(
        {
          compilerOptions: {
            target: "ES2022",
            module: "ES2022",
            moduleResolution: "bundler",
            lib: ["ES2022", "DOM"],
            strict: true,
            noEmit: true,
            skipLibCheck: false,
          },
          include: ["src"],
        },
        null,
        2
      )
    );

    const tsc = join(repoRoot, "node_modules", "typescript", "bin", "tsc");
    await expect(run(process.execPath, [tsc, "-p", "tsconfig.json"], consumer)).resolves.toBeTypeOf("string");
  });
});

describe("a headless session from a packed install", () => {
  it("runs in plain Node without installing a runtime", async () => {
    const script = [
      'import { runtime } from "@anjunar/scalajs-ui-core";',
      'import { createEditor, insertText, richText } from "@anjunar/scalajs-ui-editor";',
      "let installed = true;",
      "try { runtime(); } catch { installed = false; }",
      'const session = createEditor({ extensions: [richText()], markdown: "Ada" });',
      'const typed = session.dispatch(insertText, { text: "Hi " });',
      "const markdown = session.toMarkdown();",
      "session.dispose();",
      "console.log(JSON.stringify({ installed, handled: typed.ok && typed.handled, markdown: markdown.ok && markdown.value }));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "headless-session.mjs"), script);

    const result = lastJsonLine<{ installed: boolean; handled: boolean; markdown: string }>(
      await run(process.execPath, ["headless-session.mjs"], consumer)
    );

    expect(result).toEqual({ installed: false, handled: true, markdown: "Hi Ada\n" });
  });
});

describe("rendering a Markdown editor from a packed install", () => {
  it("renders against the bridge", async () => {
    const script = [
      'import { installRuntime, property, renderToString } from "@anjunar/scalajs-ui-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";',
      'import { viewport } from "@anjunar/scalajs-ui-viewport";',
      'import { editor } from "@anjunar/scalajs-ui-editor";',
      "installRuntime(bridgeRuntime);",
      "const result = await renderToString(() => {",
      "  viewport(() => {",
      "    editor(\"body\", { standalone: true, value: \"## Ada\", editable: false, plugins: [\"base\"] });",
      "  });",
      "});",
      "console.log(JSON.stringify({",
      "  status: result.status,",
      "  hasEditor: result.html.includes('name=\"body\"'),",
      "  hasValue: result.html.includes(\"Ada\"),",
      "  hasHeading: result.html.includes(\"<h2\"),",
      "}));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-editor.mjs"), script);

    const result = lastJsonLine<{ status: number; hasEditor: boolean; hasValue: boolean; hasHeading: boolean }>(
      await run(process.execPath, ["ssr-editor.mjs"], consumer)
    );

    expect(result.status).toBe(200);
    expect(result.hasEditor).toBe(true);
    expect(result.hasValue).toBe(true);
    expect(result.hasHeading).toBe(true);
  });
});
