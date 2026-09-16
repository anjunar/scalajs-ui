package ui.bridge

import ember.editor.core.{
  Affinity,
  Commit,
  Document,
  NodeId,
  NodeIdGenerator,
  NodeSelection,
  Origin,
  Point,
  RangeSelection,
  Schema,
  Selection
}
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

/** The plain JavaScript values a session hands out and accepts. Shapes are documented in
  * `npm/scalajs-ui-editor/src/session.ts`.
  */
private[bridge] object EditorDtos {

  def failure(message: String): js.Object =
    js.Dynamic.literal(ok = false, error = message)

  def commit(commit: Commit): js.Object =
    js.Dynamic.literal(
      revision = commit.current.revision.value.toDouble,
      documentChanged = commit.documentChanged,
      selectionChanged = commit.selectionChanged,
      origin = commit.meta.origin match {
        case Origin.User    => "user"
        case Origin.Import  => "import"
        case Origin.History => "history"
        case Origin.Remote  => "remote"
        case Origin.System  => "system"
      }
    )

  def selection(value: Option[Selection]): js.Any =
    value match {
      case None                                => null
      case Some(RangeSelection(anchor, focus)) =>
        js.Dynamic.literal(`type` = "range", anchor = point(anchor), focus = point(focus))
      case Some(NodeSelection(nodes)) =>
        js.Dynamic.literal(`type` = "node", nodes = js.Array(nodes.toVector.map(_.value).sorted*))
      case Some(_) =>
        // A selection kind an extension registered (a table's cell rectangle, say). It is valid in
        // the session, but it has no DTO here yet -- saying so beats pretending it is a range.
        js.Dynamic.literal(`type` = "other")
    }

  private def point(value: Point): js.Object =
    value match {
      case Point.Text(node, offset, affinity) =>
        js.Dynamic.literal(
          node = node.value,
          offset = offset,
          affinity = if (affinity == Affinity.Before) "before" else "after"
        )
      case Point.Children(parent, index, affinity) =>
        js.Dynamic.literal(
          parent = parent.value,
          index = index,
          affinity = if (affinity == Affinity.Before) "before" else "after"
        )
    }

  /** A selection DTO back into a selection. Shape errors throw; whether the nodes exist is the
    * session's question and comes back as a failed result.
    */
  def readSelection(value: js.Any): Selection = {
    val where  = "select()"
    val fields = EditorPayloads.fields(value, where, Set("type", "anchor", "focus", "nodes"))
    EditorPayloads.string(fields, "type", where) match {
      case "range" =>
        EditorPayloads.fields(value, where, Set("type", "anchor", "focus"))
        RangeSelection(
          readPoint(fields.get("anchor"), "anchor"),
          readPoint(fields.get("focus"), "focus")
        )
      case "node" =>
        EditorPayloads.fields(value, where, Set("type", "nodes"))
        NodeSelection(
          EditorPayloads
            .optionalStrings(fields, "nodes", where)
            .getOrElse(EditorPayloads.invalid(where, "nodes must be an array of strings"))
            .map(id => nodeId(id, where))
            .toSet
        )
      case other => EditorPayloads.invalid(where, s"type must be 'range' or 'node', not '$other'")
    }
  }

  private def nodeId(value: String, where: String): NodeId =
    NodeId.parse(value).getOrElse(EditorPayloads.invalid(where, s"'$value' is not a node id"))

  private def readPoint(value: Option[js.Any], key: String): Point = {
    val where  = s"select(): $key"
    val raw    = value.getOrElse(js.undefined)
    val fields =
      EditorPayloads.fields(raw, where, Set("node", "offset", "parent", "index", "affinity"))
    val affinity = EditorPayloads.optionalString(fields, "affinity", where) match {
      case None | Some("before") => Affinity.Before
      case Some("after")         => Affinity.After
      case Some(other)           =>
        EditorPayloads.invalid(where, s"affinity must be 'before' or 'after', not '$other'")
    }
    def index(name: String): Int =
      fields.get(name).filter(entry => js.typeOf(entry) == "number") match {
        case Some(number)
            if number.asInstanceOf[Double].isWhole && number.asInstanceOf[Double] >= 0 =>
          number.asInstanceOf[Double].toInt
        case _ => EditorPayloads.invalid(where, s"$name must be a non-negative integer")
      }
    (fields.contains("node"), fields.contains("parent")) match {
      case (true, false) =>
        EditorPayloads.fields(raw, where, Set("node", "offset", "affinity"))
        Point.Text(
          nodeId(EditorPayloads.string(fields, "node", where), where),
          index("offset"),
          affinity
        )
      case (false, true) =>
        EditorPayloads.fields(raw, where, Set("parent", "index", "affinity"))
        Point.Children(
          nodeId(EditorPayloads.string(fields, "parent", where), where),
          index("index"),
          affinity
        )
      case _ =>
        EditorPayloads.invalid(where, "expected { node, offset } or { parent, index }")
    }
  }
}
