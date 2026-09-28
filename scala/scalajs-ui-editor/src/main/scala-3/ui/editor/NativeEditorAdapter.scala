package ui.editor

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.list.*
import ember.editor.code.*
import ember.editor.codehighlighting.CodeDecorations
import ember.editor.table.*
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
import ui.core.i18n.RuntimeMessage
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
    labels: EditorText,
    surface: dom.HTMLDivElement,
    toolbar: dom.HTMLElement,
    plugins: Seq[EditorPlugin],
    toolbarMode: EditorToolbarMode,
    mediaUploader: Option[MediaUploader],
    mediaUrlPolicy: MediaUrlPolicy,
    onMediaStatus: MediaUploadStatus => Unit,
    onMarkdownChanged: String => Unit,
    onFocusChanged: Boolean => Unit
) extends AutoCloseable {
  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
  private val generator          = NodeIdGenerator.sequential("editor")
  private val history            = new History()
  private val holder             = new CompositionHolder()
  private val mediaPolicy        = NativeMediaPolicy(schemes = Set.empty)
  private val rules = MarkdownSupports.everything(media = mediaPolicy) ++ TableSupport.markdownRules
  private val markdownCodec = new UiMarkdownCodec(rules, generator, mediaUrlPolicy)
  private val field         = new EditorField(name, markdownCodec)
  private val gate          = new Extension {
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
        TableExtension(generator),
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
    val document = markdownCodec
      .decode(source, resolved.schema, NodeId("document"))
      .fold(error => throw new IllegalArgumentException(error.message), identity)
    if (hasBlocks(document)) document else editableEmpty(document.rootId)
  }

  private def hasBlocks(document: Document): Boolean =
    document.node(document.rootId).exists {
      case root: RootNode => root.children.nonEmpty
      case _              => true
    }

  // Empty Markdown decodes to a bare root, which has no caret position: typing into it does
  // nothing. The rich-text profile asks for one empty paragraph instead (RichText.emptyDocument),
  // kept under this editor's root id.
  private def editableEmpty(rootId: NodeId): Document = {
    val paragraphId = generator.next(_ == rootId)
    val textId      = generator.next(id => id == rootId || id == paragraphId)
    Document
      .build(
        resolved.schema,
        rootId,
        Vector(
          RootNode(rootId, Vector(paragraphId)),
          ParagraphNode(paragraphId, Vector(textId)),
          TextNode(textId, "")
        )
      )
      .fold(
        violations => throw new IllegalArgumentException(violations.map(_.render).mkString("; ")),
        identity
      )
  }

  def mount(markdown: String, editable: Boolean): Unit = {
    require(!closed && session == null)
    lastMarkdown = markdown
    session = EditorSession
      .create(decode(markdown), resolved, resolved.sessionConfig())
      .fold(errors => throw new IllegalArgumentException(errors.toString), identity)
    view = DocumentView.mount(session, DomCursor.root(surface), TableSupport.views)
    // Paints code blocks via the CSS Custom Highlight API, never touching the document DOM --
    // ember-code-highlighting (X02), added right before ember 1.0.0. Disposed like every other
    // one-off subscription below; painting itself degrades silently where the API is unsupported
    // (CodeDecorations.isSupported).
    val decorations = CodeDecorations.attach(session, view)
    cleanups += (() => decorations.dispose())
    selection = SelectionPort.attachTo(session, view, surface)
    // A cell rectangle dragged across a table (ember-table, X01): painted through a stylesheet in
    // the document head, never the editor's own DOM. Escape collapses it back to a caret; Tab stays
    // untouched here (TabPolicy.LeavesEditor below), so cell-to-cell Tab navigation from
    // TableBindings.tabNavigation would be dead code -- arrow keys already move the caret across
    // cells like any other block boundary.
    val cellSelection = TableSelectionView.attach(session, view, selection)
    cleanups += (() => cellSelection.dispose())
    input = BrowserInputController.attachTo(
      session,
      view,
      selection,
      EditorBindings.everything,
      EditorBindings.everythingKeyboard ++ TableBindings.keyboard,
      semantics = Some(TableSupport.everything)
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
                .getOrElse(
                  throw new IllegalArgumentException(labels.now(EditorMessages.invalidImageAddress))
                )
              val url = mediaPolicy
                .parse(checked.src)
                .fold(e => throw new IllegalArgumentException(e.message), identity)
              NativeMediaReference(url, Some(MediaId(reference.mediaId)))
            }
          case None =>
            Future.failed(new IllegalStateException(labels.now(EditorMessages.missingUploader)))
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
      StandardJsonSupport.everything(media = mediaPolicy) ++ TableSupport.json,
      TableSupport.everything,
      StandardHtmlImport
        .everything(LinkUrlPolicy.default, mediaPolicy)
        .withRules(TableSupport.htmlImport*)
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
    // Enabled only with the caret or a selection inside a table -- Tables.contextAt is the same
    // check ember-table's own demo ribbon uses, so a row/column command never fires outside one.
    def inTable[A](id: String, label: String, cmd: EditorCommand[A], payload: A) =
      ToolbarAction.command(
        id,
        label,
        session,
        cmd,
        payload,
        () =>
          CommandState(available && Tables.contextAt(session.document, session.selection).isDefined)
      )
    def mark(id: String, label: String, value: TextMark) =
      ToolbarAction.command(
        id,
        label,
        session,
        RichText.ToggleMark,
        value,
        () => ToolbarState.mark(session.state, value, available)
      )
    def groups = Vector(
      ToolbarGroup(
        labels.now(EditorMessages.history),
        Vector(
          ToolbarAction.command(
            "undo",
            labels.now(EditorMessages.undo),
            session,
            HistoryCommands.Undo,
            (),
            () => CommandState(available && history.canUndo)
          ),
          ToolbarAction.command(
            "redo",
            labels.now(EditorMessages.redo),
            session,
            HistoryCommands.Redo,
            (),
            () => CommandState(available && history.canRedo)
          )
        )
      ),
      ToolbarGroup(
        labels.now(EditorMessages.text),
        if (enabledPlugins("base"))
          Vector(
            mark("bold", labels.now(EditorMessages.bold), StandardMarks.Strong),
            mark("italic", labels.now(EditorMessages.italic), StandardMarks.Emphasis),
            mark("inline-code", labels.now(EditorMessages.inlineCode), StandardMarks.InlineCode)
          )
        else Vector.empty
      ),
      ToolbarGroup(
        labels.now(EditorMessages.paragraph),
        if (enabledPlugins("heading"))
          Vector(
            command("paragraph", labels.now(EditorMessages.paragraph), RichText.SetHeading, None),
            command(
              "heading-1",
              labels.now(EditorMessages.heading1),
              RichText.SetHeading,
              HeadingLevel.fromInt(1)
            ),
            command(
              "heading-2",
              labels.now(EditorMessages.heading2),
              RichText.SetHeading,
              HeadingLevel.fromInt(2)
            ),
            command(
              "heading-3",
              labels.now(EditorMessages.heading3),
              RichText.SetHeading,
              HeadingLevel.fromInt(3)
            ),
            command("quote", labels.now(EditorMessages.quote), RichText.Quote, ()),
            command("unquote", labels.now(EditorMessages.unquote), RichText.Unquote, ())
          )
        else Vector.empty
      ),
      ToolbarGroup(
        labels.now(EditorMessages.lists),
        if (enabledPlugins("list"))
          Vector(
            command(
              "bullet-list",
              labels.now(EditorMessages.bulletList),
              ListCommands.ToggleList,
              ListKind.Unordered
            ),
            command(
              "ordered-list",
              labels.now(EditorMessages.orderedList),
              ListCommands.ToggleList,
              ListKind.Ordered
            ),
            command("indent", labels.now(EditorMessages.indent), ListCommands.Indent, ()),
            command("outdent", labels.now(EditorMessages.outdent), ListCommands.Outdent, ())
          )
        else Vector.empty
      ),
      ToolbarGroup(
        labels.now(EditorMessages.insert),
        Vector(
          Option.when(enabledPlugins("link"))(
            ToolbarAction("link", labels.now(EditorMessages.link), () => enabled, () => openLink())
          ),
          Option.when(enabledPlugins("image"))(
            ToolbarAction(
              "image",
              labels.now(EditorMessages.editImage),
              () => CommandState(available && session.selection.nonEmpty),
              () => openImage()
            )
          ),
          Option.when(enabledPlugins("code"))(
            command(
              "code-block",
              labels.now(EditorMessages.codeBlock),
              CodeCommands.ToggleCodeBlock,
              CodeInfo()
            )
          ),
          Option.when(enabledPlugins("table"))(
            command(
              "table-insert",
              labels.now(EditorMessages.insertTable),
              TableCommands.InsertTable,
              TableSize(rows = 3, columns = 3)
            )
          ),
          Option.when(enabledPlugins("image") && mediaUploader.nonEmpty)(
            ToolbarAction(
              "upload-image",
              labels.now(EditorMessages.uploadImage),
              () => enabled,
              () => picker.open()
            )
          ),
          Option.when(enabledPlugins("horizontalRule"))(
            command("rule", labels.now(EditorMessages.rule), RichText.InsertThematicBreak, ())
          )
        ).flatten
      ),
      ToolbarGroup(
        labels.now(EditorMessages.table),
        if (enabledPlugins("table"))
          Vector(
            inTable(
              "table-row-above",
              labels.now(EditorMessages.rowAbove),
              TableCommands.InsertRow,
              RowPosition.Above
            ),
            inTable(
              "table-row-below",
              labels.now(EditorMessages.rowBelow),
              TableCommands.InsertRow,
              RowPosition.Below
            ),
            inTable(
              "table-column-before",
              labels.now(EditorMessages.columnBefore),
              TableCommands.InsertColumn,
              ColumnPosition.Before
            ),
            inTable(
              "table-column-after",
              labels.now(EditorMessages.columnAfter),
              TableCommands.InsertColumn,
              ColumnPosition.After
            ),
            inTable(
              "table-delete-row",
              labels.now(EditorMessages.deleteRow),
              TableCommands.DeleteRow,
              ()
            ),
            inTable(
              "table-delete-column",
              labels.now(EditorMessages.deleteColumn),
              TableCommands.DeleteColumn,
              ()
            ),
            inTable(
              "table-delete",
              labels.now(EditorMessages.deleteTable),
              TableCommands.DeleteTable,
              ()
            )
          )
        else Vector.empty
      )
    ).filter(_.actions.nonEmpty)
    // Keep the native ribbon visually and semantically aligned with ember-demo's DemoRibbon.
    // EditorToolbar puts the full, localised action label in aria-label/title, so icon ligatures
    // retain an accessible name instead of falling back to abbreviated visible text.
    val icons = Map(
      "undo"                -> "undo",
      "redo"                -> "redo",
      "bold"                -> "format_bold",
      "italic"              -> "format_italic",
      "inline-code"         -> "code",
      "paragraph"           -> "notes",
      "heading-1"           -> "looks_one",
      "heading-2"           -> "looks_two",
      "heading-3"           -> "looks_3",
      "quote"               -> "format_quote",
      "unquote"             -> "format_clear",
      "bullet-list"         -> "format_list_bulleted",
      "ordered-list"        -> "format_list_numbered",
      "indent"              -> "format_indent_increase",
      "outdent"             -> "format_indent_decrease",
      "link"                -> "link",
      "image"               -> "image",
      "code-block"          -> "code",
      "table-insert"        -> "table_chart",
      "upload-image"        -> "upload",
      "rule"                -> "horizontal_rule",
      "table-row-above"     -> "arrow_upward",
      "table-row-below"     -> "arrow_downward",
      "table-column-before" -> "arrow_back",
      "table-column-after"  -> "arrow_forward",
      "table-delete-row"    -> "remove",
      "table-delete-column" -> "remove",
      "table-delete"        -> "delete"
    )
    def iconGroups = groups.map(group =>
      group.copy(actions = group.actions.map(action => action.copy(icon = icons.get(action.id))))
    )
    val ribbon = toolbarMode == EditorToolbarMode.Ribbon
    // Ember's labels are constructor values. Replace only the toolbar when the locale changes;
    // the document view, selection, composition controller, uploads and undo history stay alive.
    def mountToolbar(): Unit = {
      val focusedCommand = Option(surface.ownerDocument.activeElement)
        .filter(toolbar.contains)
        .flatMap(element => Option(element.getAttribute("data-command")))
      if (bar != null) Runtime.unmount(bar)
      val translatedGroups = iconGroups
      bar = new EditorToolbar(
        session,
        selection,
        translatedGroups.flatMap(_.actions),
        name = labels.now(EditorMessages.editText),
        groups = if (ribbon) translatedGroups else Vector.empty
      )
      Runtime.mount(bar, DomCursor.root(toolbar))
      focusedCommand.foreach { id =>
        Option(toolbar.querySelector(s"[data-command='$id']"))
          .foreach(_.asInstanceOf[dom.HTMLElement].focus())
      }
    }
    mountToolbar()
    val localeChanges = labels.runtime.locale.observeWithoutInitial(_ => mountToolbar())
    cleanups += (() => localeChanges.dispose())
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
      StandardJsonSupport.everything(media = mediaPolicy) ++ TableSupport.json,
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
      EditorMessages.editLink,
      Vector(
        EditorMessages.address -> link.map(_.target.url.value).getOrElse(""),
        EditorMessages.title   -> link.flatMap(_.target.title).getOrElse("")
      )
    )(
      (target, values) => dialogService.setLink(target, values(0), values(1)),
      Option.when(link.nonEmpty)(
        EditorMessages.removeLink -> ((target: DialogTarget, _: Vector[String]) =>
          dialogService.removeLink(target)
        )
      )
    )
  }

  private def openImage(): Either[EditorError, Unit] = {
    val image = dialogService.selectedImage
    openWindow(
      if (image.nonEmpty) EditorMessages.editImage else EditorMessages.insertImage,
      Vector(
        EditorMessages.imageAddress    -> image.map(_.src.value).getOrElse(""),
        EditorMessages.alternativeText -> image.map(_.alt).getOrElse(""),
        EditorMessages.title           -> image.flatMap(_.title).getOrElse(""),
        EditorMessages.imageWidth      -> image
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
            Left(ToolbarFailure(labels.now(EditorMessages.invalidWidth)))
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
        case None => Left(ToolbarFailure(labels.now(EditorMessages.invalidImageAddress)))
      }
    )
  }

  private def openWindow(title: RuntimeMessage, fields: Vector[(RuntimeMessage, String)])(
      submit: (DialogTarget, Vector[String]) => Either[EditorError, Unit],
      extra: Option[(RuntimeMessage, (DialogTarget, Vector[String]) => Either[EditorError, Unit])] =
        None
  ): Either[EditorError, Unit] = {
    if (dialogWindow.nonEmpty)
      return Left(ToolbarFailure(labels.now(EditorMessages.closeDialog)))
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
              labels,
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
      conf.title = labels.text(title)
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
      if (editable) media.resume()
      else media.invalidate(labels.now(EditorMessages.noLongerEditable))
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
