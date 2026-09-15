package ui.editor.plugins

import ui.editor.Editor

final class ImagePlugin extends EditorPlugin {
  val name = "image"
}
object ImagePlugin {
  def imagePlugin(body: ImagePlugin ?=> Unit = {})(using editor: Editor): ImagePlugin = {
    val plugin = new ImagePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
