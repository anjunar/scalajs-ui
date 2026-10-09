/**
 * Smoke test against the real bridge.
 *
 * There is no stub half here: the stub runtime knows nothing about routing, so
 * the router facade can only be exercised against the linked Scala.js bundle.
 * This file asserts exactly what step 5 of JAVASCRIPT_API.md §9 promised and no
 * more: a route table mounts, a nested route renders through `routerOutlet()`,
 * SSR carries an error route's status, hydration claims the server tree, and a
 * `routerLink` navigates without a full page load.
 *
 * It needs the linked artifact:
 *
 *     sbt --server "scalajs-ui-bridge/fullLinkJS"
 *
 * Missing, it fails loudly rather than skipping.
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import {
  hydrate,
  fetchInto,
  installRuntime,
  mount,
  renderToString,
  resetRuntime,
  runtime,
} from "@anjunar/scalajs-ui-core";
import { classes, div, heading, text } from "@anjunar/scalajs-ui-core";
import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";
import { errorRoute, router, routerLink, routerOutlet, view, type PageBody } from "../src/index.js";

const linkedArtifact = resolve(
  process.cwd(),
  "../scalajs-ui-bridge/dist/fullopt/main.js"
);

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbt --server "scalajs-ui-bridge/fullLinkJS"\n\n` +
        `Expected: ${linkedArtifact}`
    );
  }
});

beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
  window.history.replaceState(null, "", "/");
});

function withoutAnchors(html: string): string {
  return html.replace(/<!--ui:[^>]*-->/g, "");
}

const shell = view(
  "/shell",
  () => () => {
    heading(1, () => text("router shell"));
    routerOutlet();
  },
  {
    children: [
      view("detail/:id", () => () => {
        div(() => text("detail page"));
      }),
    ],
  }
);

describe("the linked runtime", () => {
  it("is the bridge", () => {
    expect(runtime().name).toBe("scalajs-ui-bridge");
  });
});

describe("renderToString", () => {
  it("renders the matched nested route through the outlet", async () => {
    const result = await renderToString(() =>
      router([shell], { url: "/shell/detail/42" })
    );

    expect(result.status).toBe(200);
    expect(withoutAnchors(result.html)).toContain("router shell");
    expect(withoutAnchors(result.html)).toContain("detail page");
  });

  it("carries the error route's status when a path does not match", async () => {
    const result = await renderToString(() =>
      router(
        [
          view("/", () => () => text("home")),
          errorRoute("/404", 404, () => () => text("nothing here")),
        ],
        {
          url: "/missing",
          onFailure: () => "/404",
          renderErrorsOnServer: true,
        }
      )
    );

    expect(result.status).toBe(404);
    expect(withoutAnchors(result.html)).toContain("nothing here");
  });

  it("projects the original rejected loader error to onFailure", async () => {
    const expected = Object.assign(new Error("forbidden"), { status: 403 });
    let received: unknown;
    const result = await renderToString(() =>
      router(
        [
          view("/private", async () => { throw expected; }),
          errorRoute("/403", 403, () => () => text("access denied")),
        ],
        {
          url: "/private",
          onFailure: (failure) => {
            if (failure.kind === "load-failed") received = failure.error;
            return "/403";
          },
          renderErrorsOnServer: true,
        },
      )
    );

    expect(received).toBe(expected);
    expect(result.status).toBe(403);
    expect(withoutAnchors(result.html)).toContain("access denied");
  });

  it("keeps an error route's status when the router is mounted by async work", async () => {
    const result = await renderToString(() =>
      fetchInto(
        () => Promise.resolve(),
        () => router(
          [errorRoute("/404", 404, () => () => text("nothing here"))],
          { url: "/404" }
        )
      )
    );

    expect(result.status).toBe(404);
    expect(withoutAnchors(result.html)).toContain("nothing here");
  });
});

describe("mount", () => {
  it("retains the mounted page until a promise resolves and reports the navigation lifecycle", async () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    let resolvePage!: (body: PageBody) => void;
    const pending = new Promise<PageBody>(resolve => { resolvePage = resolve; });
    const events: string[] = [];
    const renderedPages: string[] = [];
    const app = mount(root, () => router([
      view("/", () => () => div(() => text("home page"))),
      view("/slow", () => pending),
    ], {
      onPageLoad: page => events.push(`load:${page.url}`),
      onPageResolved: page => {
        events.push(`resolved:${page.url}`);
        renderedPages.push(root.textContent ?? "");
      },
    }, outlet => {
      routerLink("/slow", "Slow");
      outlet();
    }));
    try {
      const home = root.querySelector("div")!;
      expect(events).toEqual(["load:/", "resolved:/"]);
      root.querySelector("a")!.click();
      expect(root.textContent).toContain("home page");
      expect(root.contains(home)).toBe(true);
      expect(root.textContent).not.toContain("Loading");
      expect(events).toEqual(["load:/", "resolved:/", "load:/slow"]);

      resolvePage(() => div(() => text("ready page")));
      await vi.waitFor(() => expect(root.textContent).toContain("ready page"));
      expect(root.contains(home)).toBe(false);
      expect(events).toEqual(["load:/", "resolved:/", "load:/slow", "resolved:/slow"]);
      expect(renderedPages[0]).toContain("home page");
      expect(renderedPages[1]).toContain("ready page");
    } finally { app.dispose(); }
  });

  it("reports completion after a nested promise and ignores superseded loaders", async () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    let resolveOld!: (body: PageBody) => void;
    let resolveChild!: (body: PageBody) => void;
    const old = new Promise<PageBody>(resolve => { resolveOld = resolve; });
    const child = new Promise<PageBody>(resolve => { resolveChild = resolve; });
    const resolved: string[] = [];
    const app = mount(root, () => router([
      view("/", () => () => text("home")),
      view("/old", () => old),
      view("/parent", () => () => { text("parent"); routerOutlet(); }, {
        children: [view("child", () => child)],
      }),
    ], { onPageResolved: page => resolved.push(page.path) }, outlet => {
      routerLink("/old", "Old");
      routerLink("/parent/child", "Nested");
      outlet();
    }));
    try {
      root.querySelectorAll("a")[0]!.click();
      root.querySelectorAll("a")[1]!.click();
      resolveOld(() => text("obsolete"));
      await new Promise(resolve => setTimeout(resolve, 20));
      expect(resolved).toEqual(["/"]);
      expect(root.textContent).not.toContain("obsolete");
      resolveChild(() => text("ready child"));
      await vi.waitFor(() => expect(root.textContent).toContain("ready child"));
      expect(resolved).toEqual(["/", "/parent/child"]);
    } finally { app.dispose(); }
  });

  it("mounts the route matched by the browser location", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () =>
      router([view("/", () => () => div(() => text("home page")))])
    );

    expect(root.textContent).toContain("home page");
    app.dispose();
  });

  it("navigates on a routerLink click without a full page load", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () =>
      router([
        view("/", () => () => {
          div(() => text("home page"));
          routerLink("/other", "To other");
        }),
        view("/other", () => () => div(() => text("other page"))),
      ])
    );

    expect(root.textContent).toContain("home page");

    const link = root.querySelector("a")!;
    expect(link.classList.contains("ui-link")).toBe(true);
    link.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));

    expect(window.location.pathname).toBe("/other");
    expect(root.textContent).toContain("other page");
    expect(root.textContent).not.toContain("home page");

    app.dispose();
  });

  it("renders a shell around the routed page, with links that navigate", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const routes = [
      view("/", () => () => div(() => text("counter page"))),
      view("/library", () => () => div(() => text("library page"))),
    ];

    const app = mount(root, () =>
      router(routes, {}, () => {
        div(() => {
          text("SHELL");
          routerLink("/library", "Library");
        });
      })
    );

    // Shell chrome and the routed page both present.
    expect(root.textContent).toContain("SHELL");
    expect(root.textContent).toContain("counter page");

    // A link in the shell -- a sibling of the outlet, not a descendant -- still
    // resolves the router and navigates.
    root.querySelector("a")!.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(window.location.pathname).toBe("/library");
    expect(root.textContent).toContain("SHELL");
    expect(root.textContent).toContain("library page");
    expect(root.textContent).not.toContain("counter page");

    app.dispose();
  });

  it("lets the shell place the routed page inside its own layout", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () =>
      router(
        [view("/", () => () => div(() => text("placed page")))],
        {},
        (outlet) =>
          div(() => {
            classes("frame");
            div(() => {
              classes("outlet");
              outlet();
            });
          })
      )
    );

    expect(root.querySelector(".frame > .outlet")?.textContent).toContain("placed page");
    app.dispose();
  });
});

describe("hydrate", () => {
  it("retains SSR nodes during a pending loader and reports completion after rendering", async () => {
    const rendered = await renderToString(() => router([
      view("/", () => () => heading(1, () => text("SSR page"))),
    ]));
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);
    const before = root.querySelector("h1");
    let resolvePage!: (body: PageBody) => void;
    const pending = new Promise<PageBody>(resolve => { resolvePage = resolve; });
    const events: string[] = [];
    const hydrating = hydrate(root, () => router([
      view("/", () => pending),
    ], {
      onPageLoad: () => events.push("load"),
      onPageResolved: () => events.push(root.textContent ?? ""),
    }));
    expect(root.querySelector("h1")).toBe(before);
    expect(events).toEqual(["load"]);
    resolvePage(() => heading(1, () => text("resolved page")));
    const app = await hydrating;
    try {
      await vi.waitFor(() => expect(events).toEqual(["load", "resolved page"]));
      expect(root.querySelector("h1")).not.toBe(before);
    } finally { app.dispose(); }
  });

  it("claims the server-rendered route tree without a fault", async () => {
    const page = (): void =>
      router([view("/", () => () => heading(1, () => text("hydrated home")))]);

    const rendered = await renderToString(page);

    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const before = root.querySelector("h1");
    expect(before).not.toBeNull();

    const app = await hydrate(root, page);

    expect(root.querySelector("h1")).toBe(before);
    expect(root.textContent).toContain("hydrated home");

    app.dispose();
  });
});
