package ui.editor.plugins

import ui.editor.Editor

final class HorizontalRulePlugin extends EditorPlugin {
  val name = "horizontalRule"
}
object HorizontalRulePlugin {
  def horizontalRulePlugin(
      body: HorizontalRulePlugin ?=> Unit = {}
  )(using editor: Editor): HorizontalRulePlugin = {
    val plugin = new HorizontalRulePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
