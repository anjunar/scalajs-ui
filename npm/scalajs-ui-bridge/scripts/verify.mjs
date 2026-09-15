import { access } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const subpaths = ["core", "router", "controls", "viewport", "forms", "editor"];

for (const file of [
  "index.js",
  ...subpaths.map((name) => `${name}.js`),
  ...subpaths.map((name) => `types/${name}.d.ts`),
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
