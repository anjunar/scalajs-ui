# @anjunar/scalajs-ui-bridge

The linked Scala.js runtime for the Scala JS UI 1.0 TypeScript API. Import this package once at application startup to install the runtime used by `@anjunar/scalajs-ui-core` and the feature packages.

## Overview

This package is the JavaScript boundary of the Scala UI implementation. It contains no second renderer or component implementation: `scalajs-ui-bridge` links the Scala.js modules and registers their factories, while the npm packages provide typed wrappers around those factories.

```text
TypeScript application
        ↓
@anjunar/scalajs-ui-* facades
        ↓
@anjunar/scalajs-ui-bridge
        ↓
Scala.js UI runtime
```

## Installation

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

The package is published with hand-written declarations in `types/index.d.ts`. The generated `dist/` bundle is produced by Scala.js and is not edited manually.

## Quick start

```ts
import { div, mount, text } from "@anjunar/scalajs-ui-core";
import "@anjunar/scalajs-ui-bridge";

mount(document.getElementById("root")!, () => {
  div(() => text("Mounted by the Scala.js UI runtime."));
});
```

The side-effect import installs `bridgeRuntime` automatically. `bridgeRuntime` is exported as a typed `UiRuntime` for integrations that need to inspect the installed runtime.

## Installing only the features you use

The bare `"."` import installs every feature (router, controls, viewport, forms, editor) --
including `scalajs-ui-editor`'s `scalajs-ember` dependency, alone about a third of the bundle. If
your app only uses a subset, import the matching subpaths instead of the bare package:

```ts
import "@anjunar/scalajs-ui-bridge/core";
import "@anjunar/scalajs-ui-bridge/controls";
import "@anjunar/scalajs-ui-bridge/viewport";
```

Each subpath (`./core`, `./router`, `./controls`, `./viewport`, `./forms`, `./editor`) installs only
that feature's registrations and only pulls in the Scala.js code it actually needs -- `./forms` also
carries `parseLocalDate`/`parseInstant`/`parseLocalDateTime`, since date fields are what uses them.
They all resolve to the same `bridgeRuntime` instance, so importing several together (e.g. `./core`
+ `./controls` + `./viewport`, as a browser-only client that never touches forms/editor server-side
would) is safe -- `installRuntime`'s duplicate-runtime guard treats repeated installs of the same
instance as a no-op. Measured on a real client bundle (`npm/scalajs-ui-landing`, core + controls +
viewport only): 387 KB gzip vs. 649 KB gzip for the bare `"."` import -- a 40% reduction from
dropping code the client never called anyway.

## Runtime boundary

The bridge projects Scala values to JavaScript-safe shapes: arrays instead of Scala collections, `null` instead of `Option`, promises at asynchronous boundaries, and opaque handles for components and properties. `mount`, `hydrate`, `renderToString`, `property`, and `listProperty` are exposed through `@anjunar/scalajs-ui-core`.

The package must be paired with matching versions of `@anjunar/scalajs-ui-core` and `@anjunar/scalajs-ui`. A second, different runtime in the same process is rejected because it would split the component tree.

## API overview

- `bridgeRuntime` — the linked `UiRuntime` instance.
- `parseLocalDate`, `parseLocalDateTime`, `parseInstant` — stable TypeScript access to the real
  `scala-java-time` values linked into the runtime.
- `UiRuntime` — the shared contract implemented by the linked bundle.
- Component and property handles — opaque projections used by the core facade.
- Registered factories — the Scala implementations behind router, controls, viewport, forms, and editor facades.

## Development

From the repository root:

```bash
sbt --server "scalajs-ui-bridge/fullLinkJS"
npm run verify --workspace npm/scalajs-ui-core
```

The linker output is generated under `npm/scalajs-ui-bridge/dist/`.
