package ui.bridge

import ember.editor.code.{CodeCommands, CodeInfo, CodeLanguage}
import ember.editor.core.{DispatchOutcome, EditorCommand, EditorSession, UpdateError}
import ember.editor.history.HistoryCommands
import ember.editor.link.{LinkCommands, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListCommands, ListKind}
import ember.editor.richtext.{HeadingLevel, RichText, StandardMarks}

import scala.scalajs.js

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
