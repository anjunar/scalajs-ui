package ui.editor.plugins

import ui.editor.Editor

final class HeadingPlugin extends EditorPlugin {
  val name = "heading"
}
object HeadingPlugin {
  def headingPlugin(body: HeadingPlugin ?=> Unit = {})(using editor: Editor): HeadingPlugin = {
    val plugin = new HeadingPlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
