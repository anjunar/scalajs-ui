package ui.editor

import ember.editor.browser.{SelectionPort, WriteIntent}
import ember.editor.core.EditorSession
import ember.editor.table.Tables
import ember.editor.toolbar.{CommandState, EditorToolbar, ToolbarAction, ToolbarGroup}
import org.scalajs.dom
import scala.scalajs.js
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.{Button, TextComponent}
import ui.core.render.{Cursor, DomNodes}
import ui.core.state.Disposable

/** Application presentation around Ember's commands. Resizing replaces only the toolbar, never the
  * document view, selection port or history. Popup actions share the ribbon's commands, presented
  * as labelled rows with vertical keyboard navigation.
  */
private[editor] final class NativeEditorToolbar(
    session: EditorSession,
    selection: SelectionPort,
    groups: Vector[ToolbarGroup],
    labels: EditorText,
    ribbon: Boolean
) extends AbstractComponent {
  val tagName           = "div"
  private val actions   = groups.flatMap(_.actions)
  private val quickIds  = Set("undo", "bold", "italic")
  private val formatIds = Set(
    "inline-code",
    "paragraph",
    "heading-1",
    "heading-2",
    "heading-3",
    "quote",
    "unquote",
    "bullet-list",
    "ordered-list",
    "indent",
    "outdent"
  )
  private val insertIds = Set("link", "image", "upload-image", "code-block", "table-insert", "rule")
  private var bar: EditorToolbar       = null
  private var panel: AbstractComponent = null
  private var panelBar: MobileActions  = null
  private var contentCursor: Cursor    = null
  private var mobile                   = false
  private var openPanel                = ""
  private var tableContext             = false
  private var message                  = ""

  private def element(component: AbstractComponent): dom.HTMLElement =
    DomNodes.option(component.host).get.asInstanceOf[dom.HTMLElement]

  private def trigger(id: String): Option[dom.HTMLElement] =
    Option(element(bar).querySelector(s"[data-command='mobile-$id']"))
      .map(_.asInstanceOf[dom.HTMLElement])

  private def panelActions(id: String): Vector[ToolbarAction] = actions.filter { action =>
    id match {
      case "format" => formatIds(action.id)
      case "insert" => insertIds(action.id)
      case _        =>
        !quickIds(action.id) && !formatIds(action.id) && !insertIds(action.id) &&
        (!action.id
          .startsWith("table-") || Tables.contextAt(session.document, session.selection).isDefined)
    }
  }

  private def panelLabel(id: String): String = labels.now(id match {
    case "format" => EditorMessages.formatting
    case "insert" => EditorMessages.insert
    case _        => EditorMessages.moreTools
  })

  /** The popup has its own presentation: icon and full label, one column, one Tab stop. */
  private final class MobileActions(items: Vector[ToolbarAction], name: String)
      extends AbstractComponent {
    val tagName         = "div"
    private var buttons = Vector.empty[(ToolbarAction, AbstractComponent)]
    private var current = 0

    private def tabStops(): Unit = buttons.zipWithIndex.foreach { case ((action, button), index) =>
      button.setAttribute("tabindex", if (index == current && action.state().enabled) "0" else "-1")
    }

    def refresh(): Unit = {
      buttons.foreach { case (action, button) =>
        val state = action.state()
        if (state.enabled) button.removeAttribute("disabled")
        else button.setAttribute("disabled", "")
        state.pressed match {
          case Some(value) => button.setAttribute("aria-pressed", value)
          case None        => button.removeAttribute("aria-pressed")
        }
      }
      if (!items.lift(current).exists(_.state().enabled))
        current = items.indexWhere(_.state().enabled)
      tabStops()
    }

    override def compose(cursor: Cursor): Unit = {
      addClass("scalajs-ui-editor__mobile-actions")
      setAttribute("role", "toolbar")
      setAttribute("aria-label", name)
      setAttribute("aria-orientation", "vertical")
      items.zipWithIndex.foreach { case (action, index) =>
        val button = new AbstractComponent {
          val tagName                                = "button"
          override def compose(cursor: Cursor): Unit = {
            setAttribute("type", "button")
            setAttribute("data-command", action.id)
            setAttribute("aria-label", action.label)
            val icon = new AbstractComponent {
              val tagName                                = "span"
              override def compose(cursor: Cursor): Unit = {
                addClass("material-icons")
                addClass("scalajs-ui-editor__mobile-action-icon")
                setAttribute("aria-hidden", "true")
                Runtime.mount(new TextComponent(action.icon.getOrElse("")), cursor, Some(this))
              }
            }
            Runtime.mount(icon, cursor, Some(this))
            Runtime.mount(new TextComponent(action.label), cursor, Some(this))
            onHandler("focus") { _ => current = index; tabStops() }
            onHandler("click") { _ =>
              if (action.state().enabled && !session.isDisposed) action.activate()
              refresh()
            }
            onHandler("keydown") { event =>
              val key = event.raw.asInstanceOf[dom.KeyboardEvent]
              if (!key.altKey && !key.ctrlKey && !key.metaKey) {
                val enabled = items.indices.filter(i => items(i).state().enabled).toVector
                if (enabled.nonEmpty) {
                  val position    = enabled.indexOf(index)
                  val destination = key.key match {
                    case "ArrowDown" => Some(enabled((position + 1) % enabled.size))
                    case "ArrowUp"   => Some(enabled((position - 1 + enabled.size) % enabled.size))
                    case "Home"      => Some(enabled.head)
                    case "End"       => Some(enabled.last)
                    case _           => None
                  }
                  destination.foreach { next =>
                    event.preventDefault()
                    element(buttons(next)._2).focus()
                  }
                }
              }
            }
          }
        }
        buttons :+= action -> Runtime.mount(button, cursor, Some(this))
      }
      refresh()
    }
  }

  def announce(value: String): Unit = {
    message = value
    if (bar != null) bar.announce(value)
  }

  def closePanel(returnFocus: Boolean = false): Unit = {
    val previous = openPanel
    openPanel = ""
    if (panel != null && element(panel).matches(":popover-open"))
      element(panel).asInstanceOf[js.Dynamic].hidePopover()
    if (previous.nonEmpty) {
      trigger(previous).foreach(_.setAttribute("aria-expanded", "false"))
      if (returnFocus) trigger(previous).foreach(_.focus())
    }
  }

  // Return to the saved model selection before running a command, including dialog actions.
  // In particular, a touch-induced blur must not replace the selection with the menu's focus.
  private def mobileAction(action: ToolbarAction): ToolbarAction = action.copy(activate = () => {
    closePanel()
    selection.scope.focus()
    selection.sync(WriteIntent.Explicit)
    val result = action.activate()
    // Formatting can replace text DOM nodes. Restore the committed selection before the next
    // native selectionchange can import the browser's collapsed range over the model selection.
    // Dialog actions have moved focus elsewhere and own their eventual return to the editor.
    if (selection.scope.focusWithin) selection.sync(WriteIntent.Explicit)
    result.left.foreach(error => announce(error.message))
    result
  })

  private def positionPanel(): Unit = if (openPanel.nonEmpty && panel != null) {
    val bounds         = element(bar).getBoundingClientRect()
    val window         = element(this).ownerDocument.defaultView
    val viewport       = window.asInstanceOf[js.Dynamic].visualViewport
    val hasViewport    = !js.isUndefined(viewport) && viewport != null
    val viewportTop    = if (!hasViewport) 0.0 else viewport.offsetTop.asInstanceOf[Double]
    val viewportLeft   = if (!hasViewport) 0.0 else viewport.offsetLeft.asInstanceOf[Double]
    val viewportHeight =
      if (!hasViewport) window.innerHeight.toDouble else viewport.height.asInstanceOf[Double]
    val viewportWidth =
      if (!hasViewport) window.innerWidth.toDouble else viewport.width.asInstanceOf[Double]
    val below     = viewportTop + viewportHeight - bounds.bottom - 8
    val above     = bounds.top - viewportTop - 8
    val downward  = below >= above
    val available = math.max(0, math.min(440, if (downward) below else above))
    val width     = math.min(320, math.min(bounds.width, math.max(0, viewportWidth - 16)))
    val anchor    = trigger(openPanel).map(_.getBoundingClientRect().left).getOrElse(bounds.left)
    val popup     = element(panel)
    popup.style.width = s"${width}px"
    popup.style.maxHeight = s"${available}px"
    popup.style.left =
      s"${math.max(viewportLeft + 8, math.min(anchor, viewportLeft + viewportWidth - width - 8))}px"
    popup.style.top = s"${
        if (downward) bounds.bottom + 4 else bounds.top - popup.getBoundingClientRect().height - 4
      }px"
  }

  private def showPanel(id: String): Unit = {
    if (openPanel == id) { closePanel(returnFocus = true); return }
    closePanel()
    if (panel != null) Runtime.unmount(panel)
    panelBar = null
    panel = Runtime.mount(
      new AbstractComponent {
        val tagName                                = "div"
        override def compose(cursor: Cursor): Unit = {
          addClass("scalajs-ui-editor__mobile-panel")
          setAttribute("popover", "auto")
          setAttribute("role", "dialog")
          setAttribute("aria-label", panelLabel(id))
          val header = new AbstractComponent {
            val tagName                                = "div"
            override def compose(cursor: Cursor): Unit = {
              addClass("scalajs-ui-editor__mobile-panel-heading")
              Runtime.mount(new TextComponent(panelLabel(id)), cursor, Some(this))
              val close = Runtime.mount(new Button(), cursor, Some(this))
              close.buttonType("button")
              close.label("close")
              close.addClass("material-icons")
              close.setAttribute("aria-label", labels.now(EditorMessages.closeTools))
              close.onClickHandler(_ => closePanel(returnFocus = true))
            }
          }
          Runtime.mount(header, cursor, Some(this))
          panelBar = Runtime.mount(
            new MobileActions(panelActions(id).map(mobileAction), panelLabel(id)),
            cursor,
            Some(this)
          )
          onHandler("keydown") { event =>
            if (event.raw.asInstanceOf[dom.KeyboardEvent].key == "Escape") {
              event.preventDefault()
              closePanel(returnFocus = true)
            }
          }
          onHandler("toggle") { _ =>
            if (!element(this).matches(":popover-open") && openPanel == id) closePanel()
          }
        }
      },
      contentCursor,
      Some(this)
    )
    openPanel = id
    trigger(id).foreach(_.setAttribute("aria-expanded", "true"))
    element(panel).asInstanceOf[js.Dynamic].showPopover()
    positionPanel()
    Option(element(panelBar).querySelector("button:not(:disabled)"))
      .foreach(_.asInstanceOf[dom.HTMLElement].focus())
  }

  private def mountBar(): Unit = {
    val focused = Option(element(this).ownerDocument.activeElement).filter(element(this).contains)
    val focusedCommand = focused.flatMap(node => Option(node.getAttribute("data-command")))
    closePanel()
    if (panel != null) { Runtime.unmount(panel); panel = null; panelBar = null }
    if (bar != null) Runtime.unmount(bar)
    val items =
      if (!mobile) actions
      else {
        val menus = Vector("format", "insert", "more")
          .filter { id =>
            // Keep More available for redo even outside a table; omit unavailable plugin groups.
            panelActions(id).nonEmpty
          }
          .map { id =>
            ToolbarAction(
              s"mobile-$id",
              panelLabel(id),
              () => CommandState(true),
              () => { showPanel(id); Right(()) },
              shortLabel = Some("Aa"),
              icon = id match {
                case "insert" => Some("add")
                case "more"   => Some("more_horiz")
                case _        => None
              }
            )
          }
        actions.filter(action => quickIds(action.id)).map(mobileAction) ++ menus
      }
    bar = Runtime.mount(
      new EditorToolbar(
        session,
        selection,
        items,
        name = labels.now(EditorMessages.editText),
        groups = if (!mobile && ribbon) groups else Vector.empty,
        extraClasses = if (mobile) Vector("scalajs-ui-editor__quickbar") else Vector.empty
      ),
      contentCursor,
      Some(this)
    )
    if (mobile) Vector("format", "insert", "more").foreach { id =>
      trigger(id).foreach { button =>
        button.setAttribute("aria-haspopup", "dialog")
        button.setAttribute("aria-expanded", "false")
      }
    }
    bar.announce(message)
    if (focused.nonEmpty) {
      val replacement =
        focusedCommand.flatMap(id => Option(element(bar).querySelector(s"[data-command='$id']")))
      replacement match {
        case Some(button) => button.asInstanceOf[dom.HTMLElement].focus()
        case None         => selection.scope.focus(); selection.sync(WriteIntent.Explicit)
      }
    }
  }

  override def compose(cursor: Cursor): Unit = {
    contentCursor = cursor
    addClass("scalajs-ui-editor__adaptive-toolbar")
    val window = element(this).ownerDocument.defaultView
    val media  = window.matchMedia("(max-width: 640px)")
    mobile = media.matches
    mountBar()
    val resized: js.Function1[dom.Event, Unit] = _ => {
      mobile = media.matches
      mountBar()
    }
    media.addEventListener("change", resized)
    addDisposable(Disposable(media.removeEventListener("change", resized)))
    val reposition: js.Function1[dom.Event, Unit] = _ => positionPanel()
    window.addEventListener("resize", reposition)
    window.addEventListener("scroll", reposition, true)
    val viewport = window.asInstanceOf[js.Dynamic].visualViewport
    if (!js.isUndefined(viewport) && viewport != null) {
      viewport.addEventListener("resize", reposition)
      viewport.addEventListener("scroll", reposition)
    }
    addDisposable(Disposable {
      window.removeEventListener("resize", reposition)
      window.removeEventListener("scroll", reposition, true)
      if (!js.isUndefined(viewport) && viewport != null) {
        viewport.removeEventListener("resize", reposition)
        viewport.removeEventListener("scroll", reposition)
      }
    })
    onHandler("pointerdown") { _ =>
      if (selection.scope.focusWithin) selection.importPendingNative()
    }
    onHandler("focusout") { event =>
      val next = event.raw.asInstanceOf[dom.FocusEvent].relatedTarget
      if (next != null && !element(this).contains(next.asInstanceOf[dom.Node])) closePanel()
    }
    val updates = session.onCommit(_ => refresh())
    addDisposable(Disposable(updates.dispose()))
  }

  def refresh(): Unit = {
    if (bar != null) bar.refresh()
    if (panelBar != null) panelBar.refresh()
    val inTable = Tables.contextAt(session.document, session.selection).isDefined
    if (tableContext != inTable && openPanel == "more") closePanel()
    tableContext = inTable
  }
}
