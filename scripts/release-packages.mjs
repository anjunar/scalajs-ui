// The one place that knows which npm packages make up a release, in publish order.
//
// scripts/publish-npm.sh, scripts/publish-npm.ps1 and scripts/verify-published-npm-set.mjs
// used to carry the same ten-entry list each; a new workspace package was versioned by
// set-version.mjs but neither published nor verified. The set is derived here from the
// workspaces instead: every non-private `@anjunar/scalajs-ui*` package, ordered so that a
// package is published after everything it depends on within the set (dependencies,
// peerDependencies and optionalDependencies alike -- the CSS package comes first because
// scalajs-ui-core declares it as a peer).
//
// Run directly, it prints one directory name below npm/ per line.
import { readdir, readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const packageRoot = resolve(repositoryRoot, "npm");

export function isUiPackage(name) {
  return typeof name === "string" && (name === "@anjunar/scalajs-ui" || name.startsWith("@anjunar/scalajs-ui-"));
}

/** `{ directory, manifest }` for every release package, dependencies before dependents. */
export async function releasePackages() {
  const packages = [];
  for (const entry of await readdir(packageRoot, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    let manifest;
    try {
      manifest = JSON.parse(await readFile(resolve(packageRoot, entry.name, "package.json"), "utf8"));
    } catch (error) {
      if (error?.code === "ENOENT") continue;
      throw error;
    }
    if (manifest.private || !isUiPackage(manifest.name)) continue;
    packages.push({ directory: entry.name, manifest });
  }
  if (packages.length === 0) throw new Error("No publishable UI packages were found below npm/.");

  const byName = new Map(packages.map((pkg) => [pkg.manifest.name, pkg]));
  const state = new Map();
  const ordered = [];
  const visit = (pkg, trail) => {
    const name = pkg.manifest.name;
    if (state.get(name) === "done") return;
    if (state.get(name) === "visiting") {
      throw new Error(`Dependency cycle between npm packages: ${[...trail, name].join(" -> ")}`);
    }
    state.set(name, "visiting");
    const dependencies = Object.keys({
      ...pkg.manifest.dependencies,
      ...pkg.manifest.peerDependencies,
      ...pkg.manifest.optionalDependencies,
    })
      .filter((dependency) => byName.has(dependency))
      .sort();
    for (const dependency of dependencies) visit(byName.get(dependency), [...trail, name]);
    state.set(name, "done");
    ordered.push(pkg);
  };
  for (const pkg of [...packages].sort((a, b) => a.manifest.name.localeCompare(b.manifest.name))) {
    visit(pkg, []);
  }
  return ordered;
}

/** Directory names below npm/, in publish order. */
export async function releasePackageDirectories() {
  return (await releasePackages()).map((pkg) => pkg.directory);
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  for (const directory of await releasePackageDirectories()) console.log(directory);
}
