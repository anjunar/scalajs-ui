package ui.bridge

import ember.editor.core.{
  EditorSession,
  ExtensionResolver,
  NodeId,
  NodeIdGenerator,
  Origin,
  Subscription,
  TransactionMeta
}
import ember.editor.history.History
import ember.editor.link.LinkUrlPolicy
import ember.editor.richtext.RichText
import ui.core.state.{Disposable => CoreDisposable}
import ui.editor.NativeEditorBinding

import scala.collection.mutable
import scala.scalajs.js

/** An Ember session, as TypeScript holds it (architecture §23).
  *
  * ==Owned and borrowed==
  *
  * A session from `createEditor` is '''owned''': [[dispose]] disposes the Ember session. A session
  * a mounted editor lends through `onSession` is '''borrowed''': the visual surface keeps it, and
  * [[dispose]] only ends this handle -- its subscriptions go, the editor stays. Either way a handle
  * whose session is gone refuses every call with an error instead of acting on a dead session.
  *
  * ==Errors==
  *
  * Misuse throws: a disposed handle, a command or selection of the wrong shape, a value that is not
  * a handle of this runtime. What the document itself decides comes back as `{ ok: false, error }`:
  * a rejected transaction, a source that does not import, an export that would lose content.
  */
final class EditorSessionHandleBridge private[bridge] (
    private[bridge] final val session: EditorSession,
    private[bridge] final val formats: EditorDocumentCodecBridge,
    private[bridge] final val context: EditorCommandContext,
    private[bridge] final val history: Option[History],
    val owned: Boolean
) extends js.Object {

  private var released      = false
  private val subscriptions = mutable.LinkedHashSet.empty[Subscription]

  def isDisposed: Boolean = released || session.isDisposed

  def revision: Double = { live("revision"); session.state.revision.value.toDouble }

  /** `false` without a `history()` extension, not an error: nothing to undo is a fact. */
  def canUndo: Boolean = { live("canUndo"); history.exists(_.canUndo) }

  def canRedo: Boolean = { live("canRedo"); history.exists(_.canRedo) }

  def selection: js.Any = { live("selection"); EditorDtos.selection(session.selection) }

  /** One command in one transaction. `handled` is false when no installed extension took the
    * command -- a list command in a session without `lists()`, say -- and the document is
    * unchanged.
    */
  def dispatch(command: js.Any, payload: js.Any = js.undefined): js.Object = {
    live("dispatch()")
    val handle = command match {
      case handle: EditorCommandHandleBridge => handle
      case _ => EditorPayloads.invalid("dispatch()", "not an editor command of this runtime")
    }
    val run = handle.bind(payload, context)
    run(session).fold(
      error => EditorDtos.failure(error.message),
      outcome =>
        js.Dynamic.literal(
          ok = true,
          handled = outcome.wasHandled,
          changed = !outcome.commit.isNoOp,
          revision = outcome.commit.current.revision.value.toDouble
        )
    )
  }

  /** Called after every commit that changed something, with a plain DTO. Returns the subscription.
    */
  def subscribe(listener: js.Any): DisposableHandle = {
    live("subscribe()")
    if (js.typeOf(listener) != "function")
      EditorPayloads.invalid("subscribe()", "listener must be a function")
    val callback     = listener.asInstanceOf[js.Function1[js.Object, Any]]
    val subscription = session.onCommit(commit => callback(EditorDtos.commit(commit)))
    subscriptions += subscription
    new DisposableHandle(CoreDisposable {
      subscriptions -= subscription
      subscription.dispose()
    })
  }

  def select(selection: js.Any): js.Object = {
    live("select()")
    val chosen = EditorDtos.readSelection(selection)
    session
      .update(_.select(chosen))
      .fold(error => EditorDtos.failure(error.message), revisionResult)
  }

  /** `{ allowLoss?: boolean }`. Strict by default: an export that would drop content fails. */
  def toMarkdown(options: js.Any = js.undefined): js.Object = {
    live("toMarkdown()")
    formats
      .encodeMarkdown(session.document, allowLoss(options, "toMarkdown()"))
      .fold(
        EditorDtos.failure,
        (value, losses) => js.Dynamic.literal(ok = true, value = value, losses = js.Array(losses*))
      )
  }

  def toJson(): js.Object = {
    live("toJson()")
    formats
      .encodeJson(session.document)
      .fold(EditorDtos.failure, value => js.Dynamic.literal(ok = true, value = value))
  }

  /** Replaces the document as an import (§14: history resets). `{ allowLoss?: boolean }`. */
  def replaceMarkdown(source: js.Any, options: js.Any = js.undefined): js.Object = {
    live("replaceMarkdown()")
    if (js.typeOf(source) != "string")
      EditorPayloads.invalid("replaceMarkdown()", "source must be a string")
    replace(
      formats.decodeMarkdown(
        source.asInstanceOf[String],
        session.document.schema,
        session.document.rootId,
        allowLoss(options, "replaceMarkdown()")
      )
    )
  }

  /** Replaces the document from the JSON envelope `toJson` produces. `{ allowLoss?: boolean }`. */
  def replaceJson(value: js.Any, options: js.Any = js.undefined): js.Object = {
    live("replaceJson()")
    replace(formats.decodeJson(value, session.document.schema, allowLoss(options, "replaceJson()")))
  }

  /** Idempotent. Ends this handle's subscriptions, and the session itself only if it is owned. */
  def dispose(): Unit =
    if (!released) {
      released = true
      subscriptions.toVector.foreach(_.dispose())
      subscriptions.clear()
      if (owned) session.dispose()
    }

  private def replace(document: Either[String, ember.editor.core.Document]): js.Object =
    document.fold(
      EditorDtos.failure,
      next =>
        session
          .update(TransactionMeta(origin = Origin.Import))(
            _.restore(next, RichText.caretAtStart(next))
          )
          .fold(error => EditorDtos.failure(error.message), revisionResult)
    )

  private def revisionResult(commit: ember.editor.core.Commit): js.Object =
    js.Dynamic.literal(ok = true, revision = commit.current.revision.value.toDouble)

  private def allowLoss(options: js.Any, where: String): Boolean =
    EditorPayloads
      .optionalBoolean(EditorPayloads.options(options, where, Set("allowLoss")), "allowLoss", where)
      .getOrElse(false)

  private def live(where: String): Unit =
    if (isDisposed)
      throw new IllegalStateException(s"$where: the editor session is disposed.")
}

private[bridge] object EditorSessionHandleBridge {

  private val where = "createEditor()"

  /** `{ extensions, markdown?, json?, allowLoss? }`. Configuration errors throw: there is no
    * session to report them on.
    */
  def create(options: js.Any): EditorSessionHandleBridge = {
    val fields =
      EditorPayloads.fields(options, where, Set("extensions", "markdown", "json", "allowLoss"))
    val handles = fields.get("extensions").filterNot(js.isUndefined) match {
      case Some(value) if js.Array.isArray(value) =>
        value.asInstanceOf[js.Array[js.Any]].toVector.zipWithIndex.map {
          case (handle: EditorExtensionHandleBridge, _) => handle
          case (_, index)                               =>
            EditorPayloads.invalid(
              where,
              s"extensions[$index] is not an editor extension of this runtime"
            )
        }
      case _ => EditorPayloads.invalid(where, "extensions must be an array")
    }
    if (handles.isEmpty) EditorPayloads.invalid(where, "extensions must not be empty")

    val generator     = NodeIdGenerator.sequential("editor")
    val contributions = handles.map(_.contribute(generator))
    val resolved      = ExtensionResolver
      .resolve(contributions.map(_.extension))
      .fold(errors => EditorPayloads.invalid(where, errors.map(_.render).mkString("; ")), identity)
    val formats = EditorDocumentCodecBridge.native(
      contributions.map(_.markdown).reduce(_ ++ _),
      contributions.map(_.json).reduce(_ ++ _),
      generator
    )
    val allowLoss = EditorPayloads.optionalBoolean(fields, "allowLoss", where).getOrElse(false)
    val markdown  = EditorPayloads.optionalString(fields, "markdown", where)
    val json      = fields.get("json").filterNot(js.isUndefined)
    val document  = (markdown, json) match {
      case (Some(_), Some(_))   => EditorPayloads.invalid(where, "pass markdown or json, not both")
      case (Some(source), None) =>
        formats.decodeMarkdown(source, resolved.schema, NodeId("document"), allowLoss)
      case (None, Some(value)) => formats.decodeJson(value, resolved.schema, allowLoss)
      case (None, None)        =>
        RichText
          .emptyDocument(resolved.schema, generator)
          .left
          .map(violations => violations.map(_.render).mkString("; "))
    }
    val session = document.fold(
      message => EditorPayloads.invalid(where, message),
      initial =>
        EditorSession
          .create(initial, resolved, resolved.sessionConfig())
          .fold(
            errors => EditorPayloads.invalid(where, errors.map(_.render).mkString("; ")),
            identity
          )
    )
    // A caret to start from, as the browser would place one. System, not User: placing it is not
    // something a user did, and it must not become an undo step.
    RichText.caretAtStart(session.document).foreach { caret =>
      session.update(TransactionMeta(origin = Origin.System))(_.select(caret))
    }
    new EditorSessionHandleBridge(
      session,
      formats,
      EditorCommandContext(
        contributions.flatMap(_.links).headOption.getOrElse(LinkUrlPolicy.default)
      ),
      contributions.flatMap(_.history).headOption,
      owned = true
    )
  }

  /** A mounted editor's session, lent to TypeScript. */
  def borrowed(binding: NativeEditorBinding): EditorSessionHandleBridge =
    new EditorSessionHandleBridge(
      binding.session,
      EditorDocumentCodecBridge.field(binding.markdown, binding.json),
      EditorCommandContext(binding.links),
      Some(binding.history),
      owned = false
    )
}

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
