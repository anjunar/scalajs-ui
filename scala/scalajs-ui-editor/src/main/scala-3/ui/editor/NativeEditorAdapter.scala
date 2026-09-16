package ui.editor

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.list.*
import ember.editor.code.*
import ember.editor.codehighlighting.CodeDecorations
import ember.editor.link.*
import ember.editor.image.{
  ImageExtension,
  ImageCommands,
  ImageNode,
  PositivePixels,
  MediaUrlPolicy as NativeMediaPolicy,
  MediaReference as NativeMediaReference,
  MediaId
}
import ember.editor.history.*
import ember.editor.markdown.{ListKind as MarkdownListKind, *}
import ember.editor.standard.*
import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.clipboard.*
import ember.editor.toolbar.*
import ember.editor.forms.{MediaCoordinator as NativeMediaCoordinator, *}
import ember.editor.ui.DocumentView
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.DomCursor
import ui.core.dsl.DslLayer
import ui.viewport.Viewport
import ui.editor.plugins.EditorPlugin
import org.scalajs.dom
import scala.scalajs.js
import scala.concurrent.{Future, ExecutionContext}
import scala.util.control.NonFatal

/** One native Ember session in the shared UI runtime. Markdown remains the form boundary. */
private[editor] final class NativeEditorAdapter(
    name: String,
    owner: AbstractComponent,
    surface: dom.HTMLDivElement,
    toolbar: dom.HTMLElement,
    plugins: Seq[EditorPlugin],
    toolbarMode: EditorToolbarMode,
    mediaUploader: Option[MediaUploader],
    mediaUrlPolicy: MediaUrlPolicy,
    onMediaStatus: MediaUploadStatus => Unit,
    onMarkdownChanged: String => Unit,
    onFocusChanged: Boolean => Unit,
    onSourceRequested: () => Unit
) extends AutoCloseable {
  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
  private val generator          = NodeIdGenerator.sequential("editor")
  private val history            = new History()
  private val holder             = new CompositionHolder()
  private val mediaPolicy        = NativeMediaPolicy(schemes = Set.empty)
  private val rules              = MarkdownSupports.everything(media = mediaPolicy)
  private val markdownCodec      = new UiMarkdownCodec(rules, generator, mediaUrlPolicy)
  private val field              = new EditorField(name, markdownCodec)
  private val gate               = new Extension {
    val id                  = ExtensionId("ui.editor.composition")
    override def contribute =
      ExtensionContributions(preCommitRules = Vector(BrowserInputController.busyRule(holder)))
  }
  private val resolved = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        ListExtension(generator),
        CodeExtension(generator),
        LinkExtension(generator),
        ImageExtension(generator, mediaPolicy),
        new ClipboardExtension(generator),
        history,
        new FormFieldExtension(field),
        gate
      )
    )
    .fold(
      errors => throw new IllegalArgumentException(errors.map(_.render).mkString("; ")),
      identity
    )
  private var session: EditorSession                    = null
  private var view: DocumentView                        = null
  private var selection: SelectionPort                  = null
  private var input: BrowserInputController             = null
  private var bar: EditorToolbar                        = null
  private var dialogWindow: Option[Viewport.WindowConf] = None
  private var closeDialog: () => Unit                   = () => ()
  private var dialogService: EditorDialogService        = null
  private var clipboard: BrowserClipboardController     = null
  private var drop: DropController                      = null
  private var media: NativeMediaCoordinator[dom.File]   = null
  private var picker: BrowserMediaPicker                = null
  private val cleanups     = scala.collection.mutable.ArrayBuffer.empty[() => Unit]
  private var applying     = false
  private var lastMarkdown = ""
  private var closed       = false
  private val fileInput    =
    surface.ownerDocument.createElement("input").asInstanceOf[dom.HTMLInputElement]

  private def decode(source: String): Document = {
    markdownCodec
      .decode(source, resolved.schema, NodeId("document"))
      .fold(error => throw new IllegalArgumentException(error.message), identity)
  }

  def mount(markdown: String, editable: Boolean): Unit = {
    require(!closed && session == null)
    lastMarkdown = markdown
    session = EditorSession
      .create(decode(markdown), resolved, resolved.sessionConfig())
      .fold(errors => throw new IllegalArgumentException(errors.toString), identity)
    view = DocumentView.mount(session, DomCursor.root(surface), ImageSupport.views)
    // Paints code blocks via the CSS Custom Highlight API, never touching the document DOM --
    // ember-code-highlighting (X02), added right before ember 1.0.0. Disposed like every other
    // one-off subscription below; painting itself degrades silently where the API is unsupported
    // (CodeDecorations.isSupported).
    val decorations = CodeDecorations.attach(session, view)
    cleanups += (() => decorations.dispose())
    selection = SelectionPort.attachTo(session, view, surface)
    input = BrowserInputController.attachTo(
      session,
      view,
      selection,
      EditorBindings.everything,
      EditorBindings.everythingKeyboard,
      semantics = Some(ImageSupport.everything)
    )
    holder.bind(input)
    val compositionHistory = HistoryBindings.groupCompositions(input, history)
    cleanups += (() => compositionHistory.dispose())
    fileInput.`type` = "file"
    fileInput.accept = "image/png,image/jpeg,image/webp,image/gif"
    fileInput.setAttribute("hidden", "")
    toolbar.parentNode.appendChild(fileInput)
    dialogService =
      new EditorDialogService(session, generator, () => available, mediaPolicy = mediaPolicy)
    val uploader = new MediaService[dom.File] {
      def upload(file: dom.File, token: MediaCancellation): Future[NativeMediaReference] = {
        val abort  = new dom.AbortController()
        val cancel = token.onCancel(() => abort.abort())
        val result = mediaUploader match {
          case Some(service) =>
            service.upload(file, abort.signal).map { reference =>
              val checked = MediaUrlPolicy
                .checked(mediaUrlPolicy, reference.src)
                .getOrElse(throw new IllegalArgumentException("Bildadresse ist nicht zulässig."))
              val url = mediaPolicy
                .parse(checked.src)
                .fold(e => throw new IllegalArgumentException(e.message), identity)
              NativeMediaReference(url, Some(MediaId(reference.mediaId)))
            }
          case None => Future.failed(new IllegalStateException("Kein Bild-Upload eingerichtet."))
        }
        result
          .recoverWith { case js.JavaScriptException(error: js.Error) =>
            Future.failed(new RuntimeException(error.message))
          }
          .andThen { case _ => cancel.dispose() }
      }
    }
    media = new NativeMediaCoordinator(
      session,
      uploader,
      generator,
      availability = () =>
        if (closed || input.mode != EditorMode.Editable) MediaAvailability.ReadOnly
        else if (input.state != ControllerState.Ready) MediaAvailability.CompositionBusy
        else MediaAvailability.Ready,
      previews = BrowserMediaPreviews.in(selection.scope.window.get),
      changed = statuses => {
        val pending = statuses.count(s =>
          s.phase match {
            case MediaPhase.Uploading | MediaPhase.Waiting(_) => true
            case _                                            => false
          }
        )
        val error = statuses.reverseIterator.map(_.phase).collectFirst {
          case MediaPhase.Failed(reason) => reason
        }
        onMediaStatus(MediaUploadStatus(pending, error))
      }
    )
    picker = new BrowserMediaPicker(
      fileInput,
      session,
      selection,
      media,
      () => "",
      report = result => result.left.foreach(e => announce(e.message))
    )
    val codec = new ClipboardCodec(
      "ui.editor.markdown",
      resolved.schema,
      StandardJsonSupport.everything(media = mediaPolicy),
      ImageSupport.everything,
      StandardHtmlImport.everything(LinkUrlPolicy.default, mediaPolicy)
    )
    clipboard = new BrowserClipboardController(
      session,
      selection,
      input,
      codec,
      report = result => result.left.foreach(e => announce(e.message)),
      files = picker.receive
    )
    val enabledPlugins =
      if (plugins.isEmpty) Set("base", "heading", "list", "link", "image", "code", "horizontalRule")
      else plugins.map(_.name).toSet
    def enabled = CommandState(
      available && session.selection.exists(_.isInstanceOf[RangeSelection])
    )
    def command[A](id: String, label: String, command: EditorCommand[A], payload: A) =
      ToolbarAction.command(id, label, session, command, payload, () => blockState(id, enabled))
    def mark(id: String, label: String, value: TextMark) =
      ToolbarAction.command(
        id,
        label,
        session,
        RichText.ToggleMark,
        value,
        () => ToolbarState.mark(session.state, value, available)
      )
    val groups = Vector(
      ToolbarGroup(
        "Verlauf",
        Vector(
          ToolbarAction.command(
            "undo",
            "Rückgängig",
            session,
            HistoryCommands.Undo,
            (),
            () => CommandState(available && history.canUndo)
          ),
          ToolbarAction.command(
            "redo",
            "Wiederholen",
            session,
            HistoryCommands.Redo,
            (),
            () => CommandState(available && history.canRedo)
          )
        )
      ),
      ToolbarGroup(
        "Text",
        if (enabledPlugins("base"))
          Vector(
            mark("bold", "Fett", StandardMarks.Strong),
            mark("italic", "Kursiv", StandardMarks.Emphasis),
            mark("inline-code", "Inline-Code", StandardMarks.InlineCode)
          )
        else Vector.empty
      ),
      ToolbarGroup(
        "Absatz",
        if (enabledPlugins("heading"))
          Vector(
            command("paragraph", "Text", RichText.SetHeading, None),
            command("heading-1", "Überschrift 1", RichText.SetHeading, HeadingLevel.fromInt(1)),
            command("heading-2", "Überschrift 2", RichText.SetHeading, HeadingLevel.fromInt(2)),
            command("heading-3", "Überschrift 3", RichText.SetHeading, HeadingLevel.fromInt(3)),
            command("quote", "Zitat", RichText.Quote, ()),
            command("unquote", "Zitat aufheben", RichText.Unquote, ())
          )
        else Vector.empty
      ),
      ToolbarGroup(
        "Listen",
        if (enabledPlugins("list"))
          Vector(
            command("bullet-list", "Aufzählung", ListCommands.ToggleList, ListKind.Unordered),
            command("ordered-list", "Nummerierung", ListCommands.ToggleList, ListKind.Ordered),
            command("indent", "Einrücken", ListCommands.Indent, ()),
            command("outdent", "Ausrücken", ListCommands.Outdent, ())
          )
        else Vector.empty
      ),
      ToolbarGroup(
        "Einfügen",
        Vector(
          Option.when(enabledPlugins("link"))(
            ToolbarAction("link", "Link", () => enabled, () => openLink())
          ),
          Option.when(enabledPlugins("image"))(
            ToolbarAction(
              "image",
              "Bild bearbeiten",
              () => CommandState(available && session.selection.nonEmpty),
              () => openImage()
            )
          ),
          Option.when(enabledPlugins("code"))(
            command("code-block", "Codeblock", CodeCommands.ToggleCodeBlock, CodeInfo())
          ),
          Option.when(enabledPlugins("table"))(
            ToolbarAction(
              "table-source",
              "Tabelle (Markdown)",
              () => CommandState(available),
              () => { onSourceRequested(); Right(()) }
            )
          ),
          Option.when(enabledPlugins("image") && mediaUploader.nonEmpty)(
            ToolbarAction("upload-image", "Bild hochladen", () => enabled, () => picker.open())
          ),
          Option.when(enabledPlugins("horizontalRule"))(
            command("rule", "Trennlinie", RichText.InsertThematicBreak, ())
          )
        ).flatten
      )
    ).filter(_.actions.nonEmpty)
    val labels = Map(
      "undo"         -> "↶",
      "redo"         -> "↷",
      "bold"         -> "F",
      "italic"       -> "K",
      "inline-code"  -> "</>",
      "heading-1"    -> "H1",
      "heading-2"    -> "H2",
      "heading-3"    -> "H3",
      "unquote"      -> "Ohne Zitat",
      "bullet-list"  -> "• Liste",
      "ordered-list" -> "1. Liste",
      "image"        -> "Bild",
      "code-block"   -> "Code",
      "table-source" -> "Tabelle",
      "rule"         -> "Linie",
      "upload-image" -> "Upload"
    )
    val labelledGroups = groups.map(group =>
      group.copy(actions =
        group.actions.map(action => action.copy(shortLabel = labels.get(action.id)))
      )
    )
    val ribbon = toolbarMode == EditorToolbarMode.Ribbon
    bar = new EditorToolbar(
      session,
      selection,
      labelledGroups.flatMap(_.actions),
      name = "Text bearbeiten",
      groups = if (ribbon) labelledGroups else Vector.empty
    )
    Runtime.mount(bar, DomCursor.root(toolbar))
    toolbar.classList.add(if (ribbon) "scalajs-ui-editor__ribbon" else "scalajs-ui-editor__compact")
    val composition = input.onComposition { _ =>
      bar.refresh()
      if (input.state == ControllerState.Ready) media.resume()
    }
    cleanups += (() => composition.dispose())
    val commits = session.onCommit { commit =>
      if (!applying && !commit.changes.isEmpty) {
        markdownCodec
          .encode(session.document)
          .fold(
            error => announce(error.message),
            result =>
              if (result != lastMarkdown) {
                lastMarkdown = result
                onMarkdownChanged(result)
              }
          )
      }
    }
    cleanups += (() => commits.dispose())
    val focusIn: js.Function1[dom.Event, Unit]  = _ => onFocusChanged(true)
    val focusOut: js.Function1[dom.Event, Unit] = _ => onFocusChanged(false)
    surface.addEventListener("focusin", focusIn)
    surface.addEventListener("focusout", focusOut)
    cleanups += (() => {
      surface.removeEventListener("focusin", focusIn);
      surface.removeEventListener("focusout", focusOut)
    })
    setEditable(editable)
  }

  /** The mounted session for [[Editor.onNativeSession]]. Only valid after [[mount]]. */
  private[editor] def binding: NativeEditorBinding = {
    require(session != null, "The editor session is not mounted.")
    new NativeEditorBinding(
      session,
      markdownCodec,
      StandardJsonSupport.everything(media = mediaPolicy),
      LinkUrlPolicy.default,
      history
    )
  }

  private def available =
    input != null && input.mode == EditorMode.Editable && input.state == ControllerState.Ready && !closed
  private def announce(message: String): Unit = if (bar != null) bar.announce(message)

  private def blockState(id: String, base: CommandState): CommandState = {
    def ancestors(start: NodeId): Vector[EditorNode] = {
      val result  = Vector.newBuilder[EditorNode]
      var current = Option(start)
      while (current.nonEmpty) {
        session.document.node(current.get).foreach(result += _)
        current = session.document.parentOf(current.get)
      }
      result.result()
    }
    val paths = session.selection.toVector.collect { case range: RangeSelection =>
      if (range.isCollapsed) Vector(ancestors(range.focus.owner))
      else RangeFormatting.runsIn(session.document, range).map(run => ancestors(run.id))
    }.flatten
    def pressed(test: Vector[EditorNode] => Boolean): CommandState = {
      val count = paths.count(test)
      base.copy(pressed =
        Some(if (count == 0) "false" else if (count == paths.size) "true" else "mixed")
      )
    }
    id match {
      case "paragraph" => pressed(_.exists(_.isInstanceOf[ParagraphNode]))
      case "heading-1" | "heading-2" | "heading-3" =>
        pressed(_.exists {
          case h: HeadingNode => h.level == HeadingLevel.fromInt(id.last.asDigit).get;
          case _              => false
        })
      case "quote"   => pressed(_.exists(_.isInstanceOf[QuoteNode]))
      case "unquote" =>
        base.copy(enabled = base.enabled && paths.exists(_.exists(_.isInstanceOf[QuoteNode])))
      case "indent" =>
        base.copy(enabled =
          base.enabled && paths.exists(
            _.collectFirst { case item: ListItemNode => item }
              .exists(item => session.document.indexOfChild(item.id).exists(_ > 0))
          )
        )
      case "outdent" =>
        base.copy(enabled = base.enabled && paths.exists(_.exists(_.isInstanceOf[ListItemNode])))
      case "code-block"  => pressed(_.exists(_.isInstanceOf[CodeBlockNode]))
      case "bullet-list" =>
        pressed(_.exists { case l: ListNode => l.kind == ListKind.Unordered; case _ => false })
      case "ordered-list" =>
        pressed(_.exists { case l: ListNode => l.kind == ListKind.Ordered; case _ => false })
      case _ => base
    }
  }

  private def openLink(): Either[EditorError, Unit] = {
    val link = session.selection
      .collect { case range: RangeSelection => range.focus }
      .flatMap(Links.linkAt(session.document, _))
    openWindow(
      "Link bearbeiten",
      Vector(
        "Adresse" -> link.map(_.target.url.value).getOrElse(""),
        "Titel"   -> link.flatMap(_.target.title).getOrElse("")
      )
    )(
      (target, values) => dialogService.setLink(target, values(0), values(1)),
      Option.when(link.nonEmpty)(
        "Link entfernen" -> ((target: DialogTarget, _: Vector[String]) =>
          dialogService.removeLink(target)
        )
      )
    )
  }

  private def openImage(): Either[EditorError, Unit] = {
    val image = dialogService.selectedImage
    openWindow(
      if (image.nonEmpty) "Bild bearbeiten" else "Bild einfügen",
      Vector(
        "Bildadresse"                               -> image.map(_.src.value).getOrElse(""),
        "Alternativtext"                            -> image.map(_.alt).getOrElse(""),
        "Titel"                                     -> image.flatMap(_.title).getOrElse(""),
        "Breite in Pixeln (leer für Originalgröße)" -> image
          .flatMap(_.width)
          .map(_.value.toString)
          .getOrElse("")
      )
    )((target, values) =>
      MediaUrlPolicy.checked(mediaUrlPolicy, values(0)) match {
        case Some(reference) =>
          val width  = Option(values(3).trim).filter(_.nonEmpty)
          val pixels = width.flatMap(_.toIntOption).flatMap(PositivePixels.parse)
          if (width.nonEmpty && pixels.isEmpty)
            Left(ToolbarFailure("Bitte eine Bildbreite zwischen 1 und 100000 eingeben."))
          else
            mediaPolicy.parse(reference.src).flatMap { src =>
              val source = NativeMediaReference(src, reference.mediaId.flatMap(MediaId.parse))
              val title  = Option(values(2).trim).filter(_.nonEmpty)
              dialogService.run(target) { tx =>
                target.image match {
                  case Some(current) =>
                    tx.select(NodeSelection(Set(current.id)))
                    tx.dispatch(
                      ImageCommands.UpdateImage,
                      image =>
                        image.copy(source = source, alt = values(1), title = title, width = pixels)
                    )
                  case None =>
                    if (!target.range.isCollapsed) tx.dispatch(ClipboardCommands.DeleteSelection)
                    tx.dispatch(
                      ImageCommands.InsertImage,
                      ImageNode(generator.nextFor(tx.document), source, values(1), title, pixels)
                    )
                }
              }
            }
        case None => Left(ToolbarFailure("Bildadresse ist nicht zulässig."))
      }
    )
  }

  private def openWindow(title: String, fields: Vector[(String, String)])(
      submit: (DialogTarget, Vector[String]) => Either[EditorError, Unit],
      extra: Option[(String, (DialogTarget, Vector[String]) => Either[EditorError, Unit])] = None
  ): Either[EditorError, Unit] = {
    if (dialogWindow.nonEmpty)
      return Left(ToolbarFailure("Bitte den geöffneten Dialog zuerst schließen."))
    if (selection.scope.focusWithin) selection.importNative()
    dialogService.capture().map { target =>
      var applied                   = false
      var finished                  = false
      var conf: Viewport.WindowConf = null
      def finish(): Unit            = if (!finished) {
        finished = true
        val mapped = dialogService.resolve(target).toOption
        dialogService.cancel(target)
        dialogWindow = None
        Viewport.closeWindow(conf)
        if (!closed) {
          selection.scope.focus()
          selection.write(
            if (applied) session.selection else mapped.orElse(session.selection),
            WriteIntent.Explicit
          )
        }
      }
      def applyResult(result: Either[EditorError, Unit]): Either[EditorError, Unit] =
        result.map { _ => applied = true }
      conf = new Viewport.WindowConf(
        body = {
          DslLayer.child(
            new EditorDialogForm(
              fields,
              values => applyResult(submit(target, values)),
              extra.map((label, action) =>
                label -> ((values: Vector[String]) => applyResult(action(target, values)))
              ),
              () => finish()
            )
          ) {}
        },
        widthPx = 520,
        heightPx = if (fields.size > 2) 460 else 360,
        onClose = Some(_ => finish())
      )
      conf.title = title
      dialogWindow = Some(conf)
      closeDialog = () => finish()
      Viewport.addWindow(conf)(using owner)
    }
  }

  def syncMarkdown(markdown: String): Unit = if (
    session != null && markdown != lastMarkdown && !closed
  ) {
    val document = decode(markdown)
    applying = true
    try {
      session
        .update(TransactionMeta(origin = Origin.Import))(_.restore(document, None))
        .fold(error => throw new IllegalArgumentException(error.message), _ => ())
      lastMarkdown = markdown
    } finally applying = false
  }

  def setEditable(editable: Boolean): Unit = if (input != null) {
    if (!editable) closeDialog()
    input.setMode(if (editable) EditorMode.Editable else EditorMode.ReadOnly)
    if (media != null) {
      if (editable) media.resume() else media.invalidate("Editor ist nicht mehr bearbeitbar.")
    }
    surface.setAttribute("aria-readonly", (!editable).toString)
    surface.classList.toggle("ember-read-only", !editable)
    toolbar.style.display = if (editable) "" else "none"
    bar.refresh()
  }

  override def close(): Unit = if (!closed) {
    closed = true
    cleanups.reverseIterator.foreach(_())
    cleanups.clear()
    closeDialog()
    if (dialogService != null) dialogService.dispose()
    if (picker != null) picker.dispose()
    if (media != null) media.dispose()
    if (drop != null) drop.dispose()
    if (clipboard != null) clipboard.dispose()
    if (bar != null) Runtime.unmount(bar)
    if (input != null) input.dispose()
    if (selection != null) selection.dispose()
    if (view != null) view.dispose()
    if (session != null) session.dispose()
    holder.release()
    fileInput.remove()
    onFocusChanged(false)
  }
}

/** A mounted editor's live Ember session, as seen by code outside this module.
  *
  * Exists for the JavaScript bridge (P29): it lends the session to TypeScript as a non-owning
  * handle. Everything here belongs to the adapter -- `session` is disposed when the visual surface
  * closes, and a borrower has to check `session.isDisposed` rather than keep a copy of anything.
  *
  * @param markdown
  *   the form's own Markdown dialect, so a borrower reads exactly the value the form submits.
  * @param json
  *   the JSON support matching the extensions this adapter installs.
  * @param links
  *   the policy [[LinkExtension]] was installed with; a link payload has to pass the same one.
  * @param history
  *   the adapter's history, for undo/redo availability.
  */
private[ui] final class NativeEditorBinding(
    val session: EditorSession,
    val markdown: FieldCodec,
    val json: ember.editor.json.JsonSupport,
    val links: LinkUrlPolicy,
    val history: History
)
