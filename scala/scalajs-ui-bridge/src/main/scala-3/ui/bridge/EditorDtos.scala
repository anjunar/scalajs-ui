package ui.bridge

import ember.editor.core.{Affinity, Commit, Document, NodeId, NodeIdGenerator, NodeSelection, Origin, Point, RangeSelection, Schema, Selection}
import ember.editor.forms.FieldCodec
import ember.editor.json.{DocumentJson, JsonSupport}
import ember.editor.markdown.{LossPolicy, MarkdownCodec, MarkdownSupport}

import scala.scalajs.js

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
