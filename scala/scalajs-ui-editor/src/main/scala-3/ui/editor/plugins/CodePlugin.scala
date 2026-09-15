package ui.editor.plugins

import ui.editor.Editor

final class CodePlugin extends EditorPlugin {
  val name = "code"
}
object CodePlugin {
  def codePlugin(body: CodePlugin ?=> Unit = {})(using editor: Editor): CodePlugin = {
    val plugin = new CodePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
