package ui.control.tabs

import ui.control.KeyboardEvents
import ui.control.tabs.Tabs
import ui.control.tabs.Tabs.{RenderMode, TabSpec}
import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.{addClass, classIf, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Div.div
import ui.core.render.{Cursor, DomHostElement, HostElement, UiEvent}
import ui.core.state.{ListProperty, Property, ReadOnlyProperty}
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.core.statement.Foreach.foreachIndexed
import ui.core.text.TextValue

final class Tabs(
    initialSelectedIndex: Int = 0,
    initialRenderMode: RenderMode = RenderMode.ActiveOnly,
    configure: Tabs ?=> Unit = Tabs.emptyConfiguration
) extends AbstractComponent {

  override val tagName: String = "section"

  val tabsProperty: ListProperty[TabSpec]      = ListProperty[TabSpec]()
  val selectedIndexProperty: Property[Int]     = Property(math.max(0, initialSelectedIndex))
  val renderModeProperty: Property[RenderMode] = Property(initialRenderMode)
  private[control] val contentRevisionProperty: Property[Int]       = Property(0)
  private val contentComponentProperty: Property[AbstractComponent] =
    Property(new EmptyTabsContent)

  /** Prefix of the ids that pair each trigger with its panel (`aria-controls`/`aria-labelledby`).
    * Allocated in the browser once hydration has claimed the server markup, which carries no ids:
    * an id handed out during composition could not be reproduced by the server.
    */
  private[control] val idPrefixProperty: Property[Option[String]] = Property(None)

  def addTab(tab: TabSpec): Unit =
    tabsProperty += tab

  def setSelectedIndex(index: Int): Unit =
    selectedIndexProperty.set(normalizedIndex(index))

  def getSelectedIndex: Int =
    normalizedIndex(selectedIndexProperty.get)

  override def compose(cursor: Cursor): Unit = {
    // Tabs, render mode and initial selection are structural configuration and
    // must be known before header and panel mount points claim SSR nodes.
    configure(using this)
    normalizeSelectedIndex()
    contentComponentProperty.setAlways(contentComponent(renderModeProperty.get))

    DslLayer.render(this, cursor) {
      addClass("ui-tabs")
      classIf("ui-tabs--empty", tabsProperty.map(_.isEmpty))

      div {
        classes = Seq("ui-tabs__header")
        setDslAttribute("role", "tablist")
        // Arrow and Home/End keys act on the focused tab (WAI-ARIA tabs pattern). Listening on the
        // tablist rather than the section keeps them away from panel content: an input inside a
        // panel must keep its caret movement.
        on("keydown")(handleKeyDown)

        foreachIndexed(tabsProperty) { (tab, index) =>
          val active = selectedIndexProperty.map(_ == index)

          button(tab.titleProperty) { trigger ?=>
            classes = Seq("ui-tabs__trigger")
            classIf("ui-tabs__trigger--active", active)
            buttonType("button")
            setDslAttribute("role", "tab")
            trigger.addDisposable(active.observe { selected =>
              setDslAttribute("aria-selected", selected.toString)
              setDslAttribute("tabindex", if (selected) "0" else "-1")
            })
            trigger.addDisposable(idPrefixProperty.observe {
              case Some(prefix) =>
                setDslAttribute("id", Tabs.tabId(prefix, index))
                setDslAttribute("aria-controls", Tabs.panelId(prefix, index))
              case None => ()
            })
            onClick(_ => setSelectedIndex(index))
          }
        }
      }

      div {
        classes = Seq("ui-tabs__content")
        dynamic(contentComponentProperty)
      }
    }

    // Rebuild listeners run after the trigger and panel listeners. A selection
    // change can therefore finish updating the current tree before ActiveOnly
    // replaces and disposes that tree during the same property notification.
    installObservers()
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) cursor.afterHydration { () =>
      if (!isDisposed) idPrefixProperty.set(Some(Tabs.nextIdPrefix(host)))
    }

  private def installObservers(): Unit = {
    addDisposable(tabsProperty.observeChanges(_ => handleTabsChanged()))
    addDisposable(selectedIndexProperty.observeWithoutInitial(handleSelectedIndexChanged))
    addDisposable(renderModeProperty.observeWithoutInitial { mode =>
      contentComponentProperty.setAlways(contentComponent(mode))
    })
  }

  private def handleTabsChanged(): Unit = {
    val current    = selectedIndexProperty.get
    val normalized = normalizedIndex(current)

    if (current == normalized) bumpContentRevision()
    else selectedIndexProperty.set(normalized)
  }

  private def handleSelectedIndexChanged(index: Int): Unit = {
    val normalized = normalizedIndex(index)

    if (index == normalized) bumpContentRevision()
    else selectedIndexProperty.set(normalized)
  }

  private def normalizeSelectedIndex(): Unit =
    selectedIndexProperty.set(normalizedIndex(selectedIndexProperty.get))

  private def normalizedIndex(index: Int): Int =
    if (tabsProperty.isEmpty) 0
    else math.max(0, math.min(tabsProperty.length - 1, index))

  private def bumpContentRevision(): Unit =
    contentRevisionProperty.setAlways(contentRevisionProperty.get + 1)

  private def contentComponent(mode: RenderMode): AbstractComponent =
    mode match {
      case RenderMode.ActiveOnly        => new ActiveTabsContent(this)
      case RenderMode.KeepMountedHidden => new MountedTabsContent(this)
    }

  private[control] def activeTab: Option[(TabSpec, Int)] =
    if (tabsProperty.isEmpty) None
    else {
      val index = getSelectedIndex
      Some(tabsProperty(index) -> index)
    }

  private def handleKeyDown(event: UiEvent): Unit =
    if (KeyboardEvents.isPlain(event)) KeyboardEvents.key(event).foreach {
      case "ArrowRight" | "ArrowDown" =>
        event.preventDefault()
        setSelectedIndex(getSelectedIndex + 1)
      case "ArrowLeft" | "ArrowUp" =>
        event.preventDefault()
        setSelectedIndex(getSelectedIndex - 1)
      case "Home" =>
        event.preventDefault()
        setSelectedIndex(0)
      case "End" =>
        event.preventDefault()
        setSelectedIndex(tabsProperty.length - 1)
      case _ => ()
    }
}

object Tabs {
  type Renderer = Tabs ?=> AbstractComponent ?=> Cursor ?=> Unit

  enum RenderMode {
    case ActiveOnly
    case KeepMountedHidden
  }

  private var idSequence = 0L

  /** A page-unique id prefix. The document check only runs against a real DOM host; a test host has
    * no document to collide in.
    */
  private def nextIdPrefix(host: HostElement): String = {
    idSequence += 1
    val prefix = s"ui-tabs-$idSequence-"
    val taken  = host match {
      case domHost: DomHostElement =>
        domHost.node.ownerDocument.querySelector(s"[id^='$prefix']") != null
      case _ => false
    }
    if (taken) nextIdPrefix(host) else prefix
  }

  private[control] def tabId(prefix: String, index: Int): String   = s"${prefix}tab-$index"
  private[control] def panelId(prefix: String, index: Int): String = s"${prefix}panel-$index"

  final class TabSpec(
      val titleProperty: ReadOnlyProperty[String],
      val render: Renderer
  ) {
    def title: String = titleProperty.get
  }

  private val emptyConfiguration: Tabs ?=> Unit =
    (_: Tabs) ?=> ()

  def tabs(
      body: Tabs ?=> Unit
  )(using AbstractComponent, Cursor): Tabs =
    DslLayer.child(new Tabs(configure = body)) {}

  def tab[T](
      title: T
  )(
      content: Renderer
  )(using tabs: Tabs, textValue: TextValue[T]): Unit =
    tabs.addTab(
      new TabSpec(
        textValue.asReadOnlyProperty(title)(using tabs),
        (currentTabs: Tabs) ?=>
          (component: AbstractComponent) ?=>
            (cursor: Cursor) ?=> content(using currentTabs)(using component)(using cursor)
      )
    )

  def selectedIndex(using tabs: Tabs): Int =
    tabs.getSelectedIndex

  def selectedIndex_=(value: Int)(using tabs: Tabs): Unit =
    tabs.setSelectedIndex(value)

  def selectedIndex_=(value: ReadOnlyProperty[Int])(using tabs: Tabs): Unit =
    tabs.addDisposable(value.observe(tabs.setSelectedIndex))

  def renderMode(using tabs: Tabs): RenderMode =
    tabs.renderModeProperty.get

  def renderMode_=(value: RenderMode)(using tabs: Tabs): Unit =
    tabs.renderModeProperty.set(value)
}
