package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel

/** The bridge's registrations, split one `object` per npm-facade feature instead of one shared
  * initializer. This split exists purely for reachability, not organization: the npm package entry
  * point used to be
  *
  * {{{
  * import "@anjunar/scalajs-ui-bridge";
  * }}}
  *
  * which forced `BridgeRuntime`'s single static initializer to register router+controls+viewport+
  * forms+editor unconditionally -- Scala.js's own dead-code elimination cannot drop code that one
  * eager initializer unconditionally touches, no matter how the linker output is chunked into
  * files. Measured: a consumer using only `ui.core` (isolated link,
  * `scala/scalajs-ui-core-browser-tests`) needs 865 KB raw / 139 KB gzip; the full bridge bundle
  * was 6.81 MB raw / 970 KB gzip, of which ~31% (unminified byte share) is `scalajs-ember`, pulled
  * in only by [[EditorRuntime]] below.
  *
  * Each object here is its own registration root with its own `@JSExportTopLevel` anchor, so the
  * npm package can expose one entry point per feature (`@anjunar/scalajs-ui-bridge/controls`,
  * `/forms`, ...) that touches only that feature's objects -- and, transitively, only the Scala.js
  * linker chunks (`ModuleSplitStyle.SmallModulesFor`, `build.sbt`) those objects actually reach.
  * The bare `"."` export (`index.js`) composes all six `installXRuntime()` calls itself, unchanged
  * in observable behavior for every existing consumer -- see [[BridgeRuntime]] below for why that
  * composition happens in `index.js` rather than inside `bridgeRuntime`'s own construction.
  *
  * `ComponentRegistry`/`UiRuntimeBridge`/`ScopeHandleBridge.component` stay exactly as before: one
  * shared, `private[bridge]` registry and one dispatcher class. Splitting registration call sites
  * changes nothing about how a registered name is looked up -- only *whether* a given name's
  * registration statement runs for a given entry point.
  */
private[bridge] object CoreRuntime {
  ComponentRegistry.register("vbox", VBoxFactory)
  ComponentRegistry.register("hbox", HBoxFactory)
  ComponentRegistry.register("button", ButtonFactory)
  ComponentRegistry.register("drawer", DrawerFactory)
  ComponentRegistry.register("drawer-navigation", DrawerNavigationFactory)
  ComponentRegistry.register("drawer-content", DrawerContentFactory)

  // `ui.core.i18n` lives in `scalajs-ui-core` itself, not in a separate facade module, so grouping
  // i18n-provider with the core-ish registrations here (rather than a seventh object) adds no new
  // module edge beyond what `scalajs-ui-core` already pays for.
  ComponentRegistry.register("i18n-provider", I18nProviderFactory)

  // moduleID "core" (not the default "main"): a plain @JSExportTopLevel(name) with no moduleID
  // funnels every export from the whole link into one shared main.js, which then has to statically
  // import every feature's chunk unconditionally just to re-export each one's name -- and an
  // unconditional `import * as X` is exactly what a downstream bundler (Vite/Rollup) cannot drop,
  // since every Scala.js-generated file has its own un-annotated (no /*#__PURE__*/) top-level type-
  // metadata call. Verified empirically: importing only installCoreRuntime *through* a shared
  // main.js still pulled in controls/router/forms/editor/ember (bundle size identical to importing
  // everything). Giving each feature its own moduleID makes the linker emit a SEPARATE physical
  // file per feature instead, so a consumer that imports only core.js never statically imports the
  // other five files at all -- confirmed to actually shrink the bundle (~3.1 MB / 482 KB gzip vs.
  // ~6.4 MB / 966 KB gzip for core-only vs. everything, real Rollup/Vite build).
  @JSExportTopLevel("installCoreRuntime", "core")
  def install(): Unit = ()
}
