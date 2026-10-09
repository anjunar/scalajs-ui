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
