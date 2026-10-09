package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.render.Cursor
import ui.core.state.{Property as CoreProperty}
import ui.editor.Editor
import ui.editor.plugins.{
  basePlugin,
  codePlugin,
  headingPlugin,
  horizontalRulePlugin,
  imagePlugin,
  linkPlugin,
  listPlugin,
  tablePlugin
}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.ExecutionContext
import ui.editor.{MediaUploader, MediaUrlPolicy, MediaReference, UploadedMediaReference}
private[bridge] object EditorFactories {
  def installPlugin(name: String)(using editor: Editor): Unit =
    name match {
      case "base"           => basePlugin()
      case "heading"        => headingPlugin()
      case "list"           => listPlugin()
      case "link"           => linkPlugin()
      case "image"          => imagePlugin()
      case "table"          => tablePlugin()
      case "code"           => codePlugin()
      case "horizontalRule" => horizontalRulePlugin()
      case other            =>
        dom.console.warn(s"editor '${editor.name}': unknown plugin '$other', ignored.")
    }
}
