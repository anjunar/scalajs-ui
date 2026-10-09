package ui.editor.plugins

import ui.editor.Editor

final class BasePlugin extends EditorPlugin {
  val name = "base"
}
object BasePlugin {
  def basePlugin(body: BasePlugin ?=> Unit = {})(using editor: Editor): BasePlugin = {
    val plugin = new BasePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
