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

private[bridge] object RouterRuntime {
  ComponentRegistry.register("router", RouterFactory)
  ComponentRegistry.register("router-outlet", RouterOutletFactory)
  ComponentRegistry.register("router-link", RouterLinkFactory)

  @JSExportTopLevel("installRouterRuntime", "router")
  def install(): Unit = ()
}

private[bridge] object ControlsRuntime {
  ComponentRegistry.register("tabs", TabsFactory)
  ComponentRegistry.register("carousel", CarouselFactory)
  ComponentRegistry.register("table-view", TableViewFactory)
  ComponentRegistry.register("data-grid", DataGridFactory)
  ComponentRegistry.register("virtual-list-view", VirtualListFactory)

  @JSExportTopLevel("installControlsRuntime", "controls")
  def install(): Unit = ()
}

private[bridge] object ViewportRuntime {
  ComponentRegistry.register("viewport", ViewportFactory)
  ComponentRegistry.register("window", WindowFactory)
  ComponentRegistry.register("overlay", OverlayFactory)
  ComponentRegistry.register("notification", NotificationFactory)

  @JSExportTopLevel("installViewportRuntime", "viewport")
  def install(): Unit = ()
}

private[bridge] object FormsRuntime {
  ComponentRegistry.register("form", FormFactory)
  ComponentRegistry.register("sub-form", SubFormFactory)
  ComponentRegistry.register("input", InputFactory)
  ComponentRegistry.register("input-container", InputContainerFactory)
  ComponentRegistry.register("field-set", FieldSetFactory)
  ComponentRegistry.register("array-form", ArrayFormFactory)
  ComponentRegistry.register("combo-box", ComboBoxFactory)
  ComponentRegistry.register("image-cropper", ImageCropperFactory)

  // JavaTimeBridge's parseInstant/parseLocalDate/parseLocalDateTime (JavaTimeBridge.scala) also use
  // moduleID "forms" -- date fields are what actually calls them, so they belong in the same
  // physical file forms.js already needs.

  @JSExportTopLevel("installFormsRuntime", "forms")
  def install(): Unit = ()
}

private[bridge] object EditorRuntime {
  ComponentRegistry.register("editor", EditorFactory)

  @JSExportTopLevel("installEditorRuntime", "editor")
  def install(): Unit = ()
}

// The P29 session API. Its own object, and deliberately not touched by EditorRuntime.install():
// reaching `editorApi` registers nothing and installs no runtime, so the npm editor facade can import
// it (npm/scalajs-ui-bridge/editor-api.js) and still load where a stub runtime is installed. Same
// moduleID as the registration, so both land in the one physical editor.js chunk.
private[bridge] object EditorApiRuntime {
  @JSExportTopLevel("editorApi", "editor")
  val editorApi: EditorApiBridge = new EditorApiBridge()
}

object BridgeRuntime {
  // Deliberately does NOT touch Core/Router/Controls/Viewport/Forms/EditorRuntime here. Scala
  // object initialization is all-or-nothing -- if constructing `bridgeRuntime` forced every
  // feature's install() to run, then merely *referencing* bridgeRuntime from any one npm subpath
  // (core.js, controls.js, ...) would drag in every other feature's registrations too, defeating
  // the split entirely. The "install everything" composition instead lives in the npm package's
  // index.js, which calls every installXRuntime() explicitly before installing bridgeRuntime --
  // see npm/scalajs-ui-bridge/index.js. bridgeRuntime's identity (needed so installRuntime's
  // same-object guard treats repeated installs across subpaths as a no-op, not a conflict) is
  // otherwise unaffected: it is always this one instance, however it was reached.
  @JSExportTopLevel("bridgeRuntime")
  val bridgeRuntime: UiRuntimeBridge = new UiRuntimeBridge()
}
