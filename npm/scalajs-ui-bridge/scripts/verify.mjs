import { access } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const subpaths = ["core", "router", "controls", "viewport", "forms", "editor"];

// "./editor-api" is the one subpath that must install nothing: the editor facade imports it, and the
// facade has to stay loadable where a stub runtime is installed. Checked first, before "." below
// installs the bridge into core's shared slot. It also proves a headless session runs in plain Node.
{
  const core = await import("@anjunar/scalajs-ui-core");
  const { editorApi } = await import(pathToFileURL(resolve(packageRoot, "editor-api.js")));
  let installed = true;
  try {
    core.runtime();
  } catch {
    installed = false;
  }
  if (installed) {
    throw new Error("importing editor-api.js installed a UI runtime");
  }
  const session = editorApi.createEditor({ extensions: [editorApi.richText()], markdown: "Ada" });
  const typed = session.dispatch(editorApi.commands.insertText, { text: "Hi " });
  const markdown = session.toMarkdown();
  session.dispose();
  if (!typed.ok || !typed.handled || markdown.value !== "Hi Ada\n" ||!session.isDisposed) {
    throw new Error("headless editor session is not usable: " + JSON.stringify({ typed, markdown }));
  }
}

for (const file of [
  "index.js",
  ...subpaths.map((name) => `${name}.js`),
  ...subpaths.map((name) => `types/${name}.d.ts`),
  "editor-api.js",
  "types/editor-api.d.ts",
  "dist/fullopt/main.js",
  ...subpaths.map((name) => `dist/fullopt/${name}.js`),
  "types/index.d.ts",
  "README.md",
]) {
  await access(resolve(packageRoot, file));
}

// The bare "." export installs everything, unchanged in observable behavior from before the
// per-feature split (BridgeRuntime.scala): still exercises scala-java-time end to end.
const { parseInstant, parseLocalDate, parseLocalDateTime } = await import(
  pathToFileURL(resolve(packageRoot, "index.js"))
);
if (parseLocalDate("2026-09-07").format("dd.MM.yyyy", "de-DE") !== "07.09.2026") {
  throw new Error("scala-java-time LocalDate export is not usable");
}
if (parseLocalDateTime("2026-09-07T20:15:00").format("dd.MM.yyyy HH:mm", "de-DE") !== "07.09.2026 20:15") {
  throw new Error("scala-java-time LocalDateTime export is not usable");
}
if (parseInstant("2026-09-07T18:15:00Z").epochMilli !== 1788804900000) {
  throw new Error("scala-java-time Instant export is not usable");
}

// Every per-feature subpath must install without throwing installRuntime's duplicate-runtime
// guard (runtime.ts) -- they all resolve to the *same* bridgeRuntime instance main.js exports, so
// installing several of them together (as a consumer combining core + controls, say, would) must
// stay a no-op, never a conflict. Importing each here after "." already installed everything is
// itself the regression check for that invariant.
for (const name of subpaths) {
  const mod = await import(pathToFileURL(resolve(packageRoot, `${name}.js`)));
  if (mod.bridgeRuntime == null) {
    throw new Error(`${name}.js did not export bridgeRuntime`);
  }
}

console.log("scalajs-ui-bridge linked artifact, feature subpaths and types verified");
