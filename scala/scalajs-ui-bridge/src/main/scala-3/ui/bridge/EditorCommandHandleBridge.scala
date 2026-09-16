package ui.bridge

import ember.editor.code.{CodeCommands, CodeInfo, CodeLanguage}
import ember.editor.core.{DispatchOutcome, EditorCommand, EditorSession, UpdateError}
import ember.editor.history.HistoryCommands
import ember.editor.link.{LinkCommands, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListCommands, ListKind}
import ember.editor.richtext.{HeadingLevel, RichText, StandardMarks}

import scala.scalajs.js

/** What a command needs from the session it runs in, beyond the session itself. */
private[bridge] final case class EditorCommandContext(links: LinkUrlPolicy)

/** A typed Ember command, as TypeScript holds it (architecture §23).
  *
  * The payload arrives as an untyped JavaScript value -- TypeScript's generic is a promise the
  * compiler checked, not one this side can rely on. [[bind]] validates it into the command's real
  * Scala payload before anything touches the session, so a wrong payload fails with a message and
  * an unchanged document, never with a half-applied transaction.
  *
  * `name` is Ember's diagnostic command name. It is not a dispatch key: nothing looks a command up
  * by it, and a plain object carrying the same name is refused.
  */
final class EditorCommandHandleBridge private[bridge] (
    val name: String,
    private[bridge] final val bind: (
        js.Any,
        EditorCommandContext
    ) => EditorSession => Either[UpdateError, DispatchOutcome]
) extends js.Object

/** The commands the facade exports. One value per Ember command; payload shapes are documented in
  * `npm/scalajs-ui-editor/src/commands.ts`.
  */
private[bridge] object EditorCommandHandles {

  private def unit(command: EditorCommand[Unit]): EditorCommandHandleBridge =
    new EditorCommandHandleBridge(
      command.name,
      (payload, _) => {
        EditorPayloads.none(payload, command.name)
        session => session.dispatch(command)
      }
    )

  private def of[A](command: EditorCommand[A], keys: String*)(
      decode: (js.Dictionary[js.Any], EditorCommandContext) => A
  ): EditorCommandHandleBridge =
    new EditorCommandHandleBridge(
      command.name,
      (payload, context) => {
        val value = decode(EditorPayloads.fields(payload, command.name, keys.toSet), context)
        session => session.dispatch(command, value)
      }
    )

  val insertText: EditorCommandHandleBridge =
    of(RichText.InsertText, "text")((fields, _) =>
      EditorPayloads.string(fields, "text", RichText.InsertText.name)
    )

  val insertParagraph: EditorCommandHandleBridge     = unit(RichText.InsertParagraph)
  val deleteBackward: EditorCommandHandleBridge      = unit(RichText.DeleteBackward)
  val deleteForward: EditorCommandHandleBridge       = unit(RichText.DeleteForward)
  val quote: EditorCommandHandleBridge               = unit(RichText.Quote)
  val unquote: EditorCommandHandleBridge             = unit(RichText.Unquote)
  val insertThematicBreak: EditorCommandHandleBridge = unit(RichText.InsertThematicBreak)

  private val marks = Map(
    "strong"    -> StandardMarks.Strong,
    "emphasis"  -> StandardMarks.Emphasis,
    "underline" -> StandardMarks.Underline,
    "strike"    -> StandardMarks.Strike,
    "code"      -> StandardMarks.InlineCode
  )

  val toggleMark: EditorCommandHandleBridge =
    of(RichText.ToggleMark, "mark") { (fields, _) =>
      val mark = EditorPayloads.string(fields, "mark", RichText.ToggleMark.name)
      marks.getOrElse(
        mark,
        EditorPayloads.invalid(
          RichText.ToggleMark.name,
          s"mark must be one of ${marks.keys.toVector.sorted.mkString(", ")}, not '$mark'"
        )
      )
    }

  /** `{ level: 1..6 }` for a heading, `{ level: null }` for a paragraph. */
  val setHeading: EditorCommandHandleBridge =
    of(RichText.SetHeading, "level") { (fields, _) =>
      val name = RichText.SetHeading.name
      fields.get("level") match {
        case Some(null)                                  => None
        case Some(level) if js.typeOf(level) == "number" =>
          val number = level.asInstanceOf[Double]
          Some(
            Option
              .when(number.isWhole)(number.toInt)
              .flatMap(HeadingLevel.fromInt)
              .getOrElse(EditorPayloads.invalid(name, s"level must be 1 to 6 or null, not $number"))
          )
        case _ => EditorPayloads.invalid(name, "level must be 1 to 6 or null")
      }
    }

  val toggleList: EditorCommandHandleBridge =
    of(ListCommands.ToggleList, "kind") { (fields, _) =>
      EditorPayloads.string(fields, "kind", ListCommands.ToggleList.name) match {
        case "bullet"  => ListKind.Unordered
        case "ordered" => ListKind.Ordered
        case other     =>
          EditorPayloads.invalid(
            ListCommands.ToggleList.name,
            s"kind must be 'bullet' or 'ordered', not '$other'"
          )
      }
    }

  val indent: EditorCommandHandleBridge  = unit(ListCommands.Indent)
  val outdent: EditorCommandHandleBridge = unit(ListCommands.Outdent)

  /** `{ language?: string, meta?: string }`. Toggling off ignores both. */
  val toggleCodeBlock: EditorCommandHandleBridge =
    of(CodeCommands.ToggleCodeBlock, "language", "meta") { (fields, _) =>
      val name     = CodeCommands.ToggleCodeBlock.name
      val language = EditorPayloads.optionalString(fields, "language", name).map { value =>
        CodeLanguage
          .parse(value)
          .getOrElse(EditorPayloads.invalid(name, s"'$value' is not a code language"))
      }
      CodeInfo(language, EditorPayloads.optionalString(fields, "meta", name).filter(_.nonEmpty))
    }

  /** `{ href: string, title?: string }`, checked against the session's link policy. */
  val setLink: EditorCommandHandleBridge =
    of(LinkCommands.SetLink, "href", "title") { (fields, context) =>
      val name = LinkCommands.SetLink.name
      val href = EditorPayloads.string(fields, "href", name)
      val url  = context.links
        .parse(href)
        .fold(error => EditorPayloads.invalid(name, error.message), identity)
      LinkTarget(url, EditorPayloads.optionalString(fields, "title", name).filter(_.nonEmpty))
    }

  val removeLink: EditorCommandHandleBridge = unit(LinkCommands.RemoveLink)
  val undo: EditorCommandHandleBridge       = unit(HistoryCommands.Undo)
  val redo: EditorCommandHandleBridge       = unit(HistoryCommands.Redo)
}

/** Validation of JavaScript values at the editor boundary.
  *
  * Every failure is an `IllegalArgumentException` naming the command or factory, thrown before any
  * session is touched -- the same kind the other bridge factories throw for a misuse.
  */
private[bridge] object EditorPayloads {

  def invalid(where: String, message: String): Nothing =
    throw new IllegalArgumentException(s"$where: $message")

  private def isPlainObject(value: js.Any): Boolean =
    value != null && js.typeOf(value) == "object" && !js.Array.isArray(value)

  /** A command without a payload accepts nothing, not even `{}` -- a payload there is a mistake. */
  def none(payload: js.Any, where: String): Unit =
    if (!js.isUndefined(payload)) invalid(where, "takes no payload")

  /** A payload object with no keys other than `allowed`. */
  def fields(payload: js.Any, where: String, allowed: Set[String]): js.Dictionary[js.Any] = {
    if (!isPlainObject(payload)) invalid(where, "expected an object")
    val dictionary = payload.asInstanceOf[js.Dictionary[js.Any]]
    val unknown    = dictionary.keys.filterNot(allowed).toVector.sorted
    if (unknown.nonEmpty) invalid(where, s"unknown field(s) ${unknown.mkString(", ")}")
    dictionary
  }

  /** An options object with no keys other than `allowed`; `undefined` means none. */
  def options(value: js.Any, where: String, allowed: Set[String]): js.Dictionary[js.Any] =
    if (js.isUndefined(value)) js.Dictionary.empty
    else fields(value, where, allowed)

  def string(fields: js.Dictionary[js.Any], key: String, where: String): String =
    optionalString(fields, key, where).getOrElse(invalid(where, s"$key must be a string"))

  def optionalString(fields: js.Dictionary[js.Any], key: String, where: String): Option[String] =
    fields.get(key).filterNot(js.isUndefined).map { value =>
      if (js.typeOf(value) == "string") value.asInstanceOf[String]
      else invalid(where, s"$key must be a string")
    }

  def optionalBoolean(fields: js.Dictionary[js.Any], key: String, where: String): Option[Boolean] =
    fields.get(key).filterNot(js.isUndefined).map { value =>
      if (js.typeOf(value) == "boolean") value.asInstanceOf[Boolean]
      else invalid(where, s"$key must be a boolean")
    }

  def optionalStrings(
      fields: js.Dictionary[js.Any],
      key: String,
      where: String
  ): Option[Vector[String]] =
    fields.get(key).filterNot(js.isUndefined).map { value =>
      if (!js.Array.isArray(value)) invalid(where, s"$key must be an array of strings")
      value.asInstanceOf[js.Array[js.Any]].toVector.map { entry =>
        if (js.typeOf(entry) == "string") entry.asInstanceOf[String]
        else invalid(where, s"$key must be an array of strings")
      }
    }
}
