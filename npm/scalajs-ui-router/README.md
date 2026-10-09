# @anjunar/scalajs-ui-router

Typed route tables, nested outlets, navigation links, and route failures for Scala JS UI 1.0 TypeScript applications.

## Overview

The package is a TypeScript facade over `ui.router.Router`. Matching, async loading, history handling, localized URLs, and SSR status remain in the Scala.js runtime linked by `@anjunar/scalajs-ui-bridge`.

## Installation

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-router @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

## Quick start

```ts
import { div, text } from "@anjunar/scalajs-ui-core";
import { router, routerLink, routerOutlet, view } from "@anjunar/scalajs-ui-router";

const routes = [
  view("/", async () => () => div(() => text("Home"))),
  view("/docs", async () => () => {
    div(() => {
      text("Documentation");
      routerOutlet();
    });
  }, {
    children: [view("page/:id", async (context) => () => div(() => text(context.params["id"] ?? "")))],
  }),
];

router(routes, {}, (outlet) => {
  div(() => {
    routerLink("/", "Home");
    div(() => outlet());
  });
});
```

`view(path, load, options?)` loaders return a `PageBody`, a synchronous body that composes through the core DSL. `errorRoute(path, status, load)` declares a route with a 4xx or 5xx status.

## SSR and hydration

Pending loaders retain the mounted page until their promise resolves. The initial outlet is
empty while loading; no loading page is rendered. Nested loaders start where their parent
calls `routerOutlet()`, so the parent can appear before an asynchronous child.
Use lifecycle callbacks for a progress bar in the application shell:

```ts
const busy = property(false);
router(routes, {
  onPageLoad: () => busy.set(true),
  onPageResolved: () => busy.set(false),
}, appShell);
```

Import `property` from `@anjunar/scalajs-ui-core` and bind your progress indicator to `busy`.
Callbacks receive `{ path, url }`, include the initial load, and are released with the router.
Completion waits for started nested loaders and error boundaries; superseded navigations
emit no completion. History updates at navigation start; scrolling waits for resolution.

```ts
import { hydrate, renderToString } from "@anjunar/scalajs-ui-core";
import "@anjunar/scalajs-ui-bridge";

const server = await renderToString(() => router(routes, {
  url: requestPath,
  onFailure: () => "/404",
  renderErrorsOnServer: true,
}, appShell));

await hydrate(document.getElementById("root")!, () => router(routes, {}, appShell));
```

Using only `@anjunar/scalajs-ui-router` (no controls/forms/editor)? Import `@anjunar/scalajs-ui-bridge/router` instead of the bare package to skip installing the other features -- see that package's README.

The server resolves the first request and returns the matched route status. `routerLink` remains an ordinary anchor without JavaScript; hydration enhances it with client-side navigation. A shell receives `outlet`, so it can place the root route inside its own Drawer or Viewport. Existing zero-argument shell callbacks remain supported and receive the routed page as their next sibling. Nested routes render only where a parent calls `routerOutlet()`.

## API overview

- `view`, `errorRoute`, `router`
- `routerOutlet`, `routerLink`
- `RouteContext`, `RouterConfig`, `RouterPageEvent`, `RouteDefinition`, `RouteFailure`
- `PageBody`, `RouteLoad`, `ViewOptions`, `LinkOptions`

## Related modules

- [`@anjunar/scalajs-ui-core`](../scalajs-ui-core/README.md) provides the DSL and SSR entry points.
- [`@anjunar/scalajs-ui-viewport`](../scalajs-ui-viewport/README.md) commonly wraps the routed application.
