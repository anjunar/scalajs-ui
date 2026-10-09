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
import ember.editor.standard.{
  ImageJsonSupport,
  MarkdownRules,
  MarkdownSupports,
  StandardJsonCodecs,
  StandardJsonSupport
}

import scala.scalajs.js

/** What one extension brings into one session: the Ember extension itself and the formats that know
  * its nodes. Built fresh per session -- an Ember extension such as [[History]] holds the session
  * it was installed into, so one instance can never serve two.
  */
private[bridge] final case class EditorContribution(
    extension: Extension,
    markdown: MarkdownSupport,
    json: JsonSupport,
    links: Option[LinkUrlPolicy] = None,
    history: Option[History] = None
)
