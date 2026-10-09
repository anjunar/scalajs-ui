package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel
private[bridge] object EditorRuntime {
  ComponentRegistry.register("editor", EditorFactory)

  @JSExportTopLevel("installEditorRuntime", "editor")
  def install(): Unit = ()
}
