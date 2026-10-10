package ui.bridge

import ember.editor.core.{EditorSession, ExtensionResolver, NodeId, NodeIdGenerator, Origin, Subscription, TransactionMeta}
import ember.editor.history.History
import ember.editor.link.LinkUrlPolicy
import ember.editor.richtext.RichText
import ui.core.state.{Disposable as CoreDisposable}
import ui.editor.NativeEditorBinding

import scala.collection.mutable
import scala.scalajs.js

/** Everything the editor facade reaches, as one exported value (`editorApi`, moduleID "editor").
  *
  * One object rather than a dozen top-level exports so that `@anjunar/scalajs-ui-bridge/editor-api`
  * can re-export it without installing anything: the facade must be importable where a different
  * runtime -- the stub, in tests -- is installed, and only calling `createEditor` needs this one.
  */
final class EditorApiBridge private[bridge] () extends js.Object {

  def createEditor(options: js.Any): EditorSessionHandleBridge =
    EditorSessionHandleBridge.create(options)

  def richText(): EditorExtensionHandleBridge = EditorExtensionHandles.richText()

  def history(): EditorExtensionHandleBridge = EditorExtensionHandles.history()

  def lists(): EditorExtensionHandleBridge = EditorExtensionHandles.lists()

  def code(): EditorExtensionHandleBridge = EditorExtensionHandles.code()

  def links(options: js.Any = js.undefined): EditorExtensionHandleBridge =
    EditorExtensionHandles.links(options)

  def images(options: js.Any = js.undefined): EditorExtensionHandleBridge =
    EditorExtensionHandles.images(options)

  val commands: js.Object = js.Dynamic.literal(
    insertText = EditorCommandHandles.insertText,
    insertParagraph = EditorCommandHandles.insertParagraph,
    deleteBackward = EditorCommandHandles.deleteBackward,
    deleteForward = EditorCommandHandles.deleteForward,
    toggleMark = EditorCommandHandles.toggleMark,
    setHeading = EditorCommandHandles.setHeading,
    quote = EditorCommandHandles.quote,
    unquote = EditorCommandHandles.unquote,
    insertThematicBreak = EditorCommandHandles.insertThematicBreak,
    toggleList = EditorCommandHandles.toggleList,
    indent = EditorCommandHandles.indent,
    outdent = EditorCommandHandles.outdent,
    toggleCodeBlock = EditorCommandHandles.toggleCodeBlock,
    setLink = EditorCommandHandles.setLink,
    removeLink = EditorCommandHandles.removeLink,
    undo = EditorCommandHandles.undo,
    redo = EditorCommandHandles.redo
  )
}
