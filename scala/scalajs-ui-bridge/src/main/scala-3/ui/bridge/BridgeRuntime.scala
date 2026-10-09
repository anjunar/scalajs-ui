package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel
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
