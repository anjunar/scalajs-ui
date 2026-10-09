package ui.editor.plugins

import ui.editor.Editor

final class TablePlugin extends EditorPlugin {
  val name = "table"
}
object TablePlugin {
  def tablePlugin(body: TablePlugin ?=> Unit = {})(using editor: Editor): TablePlugin = {
    val plugin = new TablePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
