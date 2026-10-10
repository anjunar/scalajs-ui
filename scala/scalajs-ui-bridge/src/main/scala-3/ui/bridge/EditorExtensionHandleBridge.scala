package ui.bridge

import ember.editor.code.CodeExtension
import ember.editor.core.{Extension, NodeIdGenerator}
import ember.editor.history.History
import ember.editor.image.{ImageExtension, MediaUrlPolicy}
import ember.editor.json.JsonSupport
import ember.editor.link.{LinkExtension, LinkUrlPolicy}
import ember.editor.list.ListExtension
import ember.editor.markdown.MarkdownSupport
import ember.editor.richtext.RichText
import ember.editor.standard.{ImageJsonSupport, MarkdownRules, MarkdownSupports, StandardJsonCodecs, StandardJsonSupport}

import scala.scalajs.js

/** An extension factory's result, as TypeScript holds it (architecture §23: "Ext-Fabriken statt
  * Plugin-Stringliste").
  *
  * Not an installed extension but a recipe for one: `createEditor` calls [[contribute]] once per
  * session, so the same handle can configure any number of sessions. The options were validated
  * when the handle was made, which is where a wrong option belongs -- at the call that passed it,
  * not at a later `createEditor` far away.
  *
  * Identity is the class: a value that is not an instance of this class from this linked runtime is
  * refused by `createEditor`, whatever its shape.
  */
final class EditorExtensionHandleBridge private[bridge] (
    val name: String,
    private[bridge] final val contribute: NodeIdGenerator => EditorContribution
) extends js.Object
