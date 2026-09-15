package ui.editor.plugins

import ui.editor.Editor

final class ListPlugin extends EditorPlugin {
  val name = "list"
}
object ListPlugin {
  def listPlugin(body: ListPlugin ?=> Unit = {})(using editor: Editor): ListPlugin = {
    val plugin = new ListPlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
