package ui.bridge

import ember.editor.core.{Affinity, Commit, Document, NodeId, NodeIdGenerator, NodeSelection, Origin, Point, RangeSelection, Schema, Selection}
import ember.editor.forms.FieldCodec
import ember.editor.json.{DocumentJson, JsonSupport}
import ember.editor.markdown.{LossPolicy, MarkdownCodec, MarkdownSupport}

import scala.scalajs.js

/** How a session's document crosses the JavaScript boundary: as Markdown or as the Ember JSON
  * envelope, never as a Scala object graph (architecture §23: "JSON-DTOs bleiben die
  * Austauschgrenze").
  *
  * Failures are values here, not exceptions: a Markdown source that cannot be imported is ordinary
  * data, and the caller decides what to show.
  */
private[bridge] trait EditorDocumentCodecBridge {

  def json: JsonSupport

  /** Markdown and the messages of what it could not carry. Losses are only ever non-empty when
    * `allowLoss` was asked for.
    */
  def encodeMarkdown(
      document: Document,
      allowLoss: Boolean
  ): Either[String, (String, Vector[String])]

  def decodeMarkdown(
      source: String,
      schema: Schema,
      rootId: NodeId,
      allowLoss: Boolean
  ): Either[String, Document]

  /** The JSON envelope as a plain JavaScript value, not as text -- what `JSON.parse` would give. */
  final def encodeJson(document: Document): Either[String, js.Any] =
    DocumentJson
      .encodeToString(document, json)
      .fold(errors => Left(errors.map(_.render).mkString("; ")), text => Right(js.JSON.parse(text)))

  final def decodeJson(
      value: js.Any,
      schema: Schema,
      allowLoss: Boolean
  ): Either[String, Document] =
    if (js.isUndefined(value)) Left("The JSON document is undefined.")
    else
      DocumentJson
        .decodeString(js.JSON.stringify(value), schema, json)
        .left
        .map(_.map(_.render).mkString("; "))
        .flatMap { decoded =>
          if (decoded.diagnostics.nonEmpty && !allowLoss)
            Left(decoded.diagnostics.map(_.render).mkString("; "))
          else Right(decoded.document)
        }
}

private[bridge] object EditorDocumentCodecBridge {

  /** The formats of a session this bridge created: exactly the rules its extensions brought. */
  def native(
      markdown: MarkdownSupport,
      jsonSupport: JsonSupport,
      generator: NodeIdGenerator
  ): EditorDocumentCodecBridge =
    new EditorDocumentCodecBridge {
      val json: JsonSupport = jsonSupport

      def encodeMarkdown(document: Document, allowLoss: Boolean) =
        MarkdownCodec
          .encode(document, markdown, if (allowLoss) LossPolicy.AllowLossy else LossPolicy.Strict)
          .fold(
            error => Left(error.message),
            encoded => Right((encoded.source, encoded.losses.map(_.render)))
          )

      def decodeMarkdown(source: String, schema: Schema, rootId: NodeId, allowLoss: Boolean) =
        MarkdownCodec
          .decode(source, schema, markdown, generator, rootId)
          .left
          .map(_.message)
          .flatMap { decoded =>
            val losses = decoded.diagnostics.filter(_.loss)
            if (losses.nonEmpty && !allowLoss) Left(losses.map(_.render).mkString("; "))
            else Right(decoded.document)
          }
    }

  /** The formats of a mounted editor: its form's own Markdown dialect, so a borrower reads what the
    * form submits. That codec is strict by construction; `allowLoss` permits a loss it never makes.
    */
  def field(codec: FieldCodec, jsonSupport: JsonSupport): EditorDocumentCodecBridge =
    new EditorDocumentCodecBridge {
      val json: JsonSupport = jsonSupport

      def encodeMarkdown(document: Document, allowLoss: Boolean) =
        codec
          .encode(document)
          .fold(error => Left(error.message), value => Right((value, Vector.empty)))

      def decodeMarkdown(source: String, schema: Schema, rootId: NodeId, allowLoss: Boolean) =
        codec.decode(source, schema, rootId).left.map(_.message)
    }
}
