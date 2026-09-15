package ui.editor.plugins

import ui.editor.Editor

final class LinkPlugin extends EditorPlugin {
  val name = "link"
}
object LinkPlugin {
  def linkPlugin(body: LinkPlugin ?=> Unit = {})(using editor: Editor): LinkPlugin = {
    val plugin = new LinkPlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
