package ui.bridge

import ember.editor.code.{CodeCommands, CodeInfo, CodeLanguage}
import ember.editor.core.{DispatchOutcome, EditorCommand, EditorSession, UpdateError}
import ember.editor.history.HistoryCommands
import ember.editor.link.{LinkCommands, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListCommands, ListKind}
import ember.editor.richtext.{HeadingLevel, RichText, StandardMarks}

import scala.scalajs.js

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
