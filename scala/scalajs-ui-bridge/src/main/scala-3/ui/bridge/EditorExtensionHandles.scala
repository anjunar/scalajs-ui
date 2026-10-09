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

/** The factories behind `richText()`, `history()`, `lists()`, `links()`, `code()` and `images()`.
  */
private[bridge] object EditorExtensionHandles {

  def richText(): EditorExtensionHandleBridge =
    new EditorExtensionHandleBridge(
      "richText",
      generator =>
        EditorContribution(
          RichText(generator),
          MarkdownSupports.richText,
          StandardJsonSupport.richText
        )
    )

  def history(): EditorExtensionHandleBridge =
    new EditorExtensionHandleBridge(
      "history",
      _ => {
        val history = new History()
        EditorContribution(history, MarkdownSupport.of(), JsonSupport.of(), history = Some(history))
      }
    )

  def lists(): EditorExtensionHandleBridge =
    new EditorExtensionHandleBridge(
      "lists",
      generator =>
        EditorContribution(
          ListExtension(generator),
          MarkdownSupport.of(MarkdownRules.list),
          JsonSupport.of(StandardJsonCodecs.list, StandardJsonCodecs.listItem)
        )
    )

  def code(): EditorExtensionHandleBridge =
    new EditorExtensionHandleBridge(
      "code",
      generator =>
        EditorContribution(
          CodeExtension(generator),
          MarkdownSupport.of(MarkdownRules.code),
          JsonSupport.of(StandardJsonCodecs.code)
        )
    )

  /** `{ schemes?: string[], allowRelative?: boolean }`, defaulting to Ember's own link policy. */
  def links(options: js.Any): EditorExtensionHandleBridge = {
    val fields   = EditorPayloads.options(options, "links()", Set("schemes", "allowRelative"))
    val defaults = LinkUrlPolicy.default
    val policy   = LinkUrlPolicy(
      schemes = EditorPayloads
        .optionalStrings(fields, "schemes", "links()")
        .map(_.toSet)
        .getOrElse(defaults.schemes),
      allowRelative = EditorPayloads
        .optionalBoolean(fields, "allowRelative", "links()")
        .getOrElse(defaults.allowRelative)
    )
    new EditorExtensionHandleBridge(
      "links",
      generator =>
        EditorContribution(
          LinkExtension(generator, policy),
          MarkdownSupport.of(MarkdownRules.links(policy)),
          JsonSupport.of(StandardJsonCodecs.link(policy)),
          links = Some(policy)
        )
    )
  }

  /** `{ schemes?: string[], allowRelative?: boolean, hosts?: string[] }`, defaulting to Ember's
    * media policy (HTTPS and relative paths, any host).
    */
  def images(options: js.Any): EditorExtensionHandleBridge = {
    val fields =
      EditorPayloads.options(options, "images()", Set("schemes", "allowRelative", "hosts"))
    val defaults = MediaUrlPolicy.default
    val policy   = MediaUrlPolicy(
      schemes = EditorPayloads
        .optionalStrings(fields, "schemes", "images()")
        .map(_.toSet)
        .getOrElse(defaults.schemes),
      allowRelative = EditorPayloads
        .optionalBoolean(fields, "allowRelative", "images()")
        .getOrElse(defaults.allowRelative),
      hosts = EditorPayloads.optionalStrings(fields, "hosts", "images()").map(_.toSet)
    )
    new EditorExtensionHandleBridge(
      "images",
      generator =>
        EditorContribution(
          ImageExtension(generator, policy),
          MarkdownSupport.of(MarkdownRules.images(policy)),
          JsonSupport.of(ImageJsonSupport.codec(policy))
        )
    )
  }
}
