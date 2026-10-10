package ui.viewport

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.di.Context
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{render, renderInto}
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.{CompositeDisposable, Disposable, ListProperty, Property, ReadOnlyProperty}
import ui.core.statement.Foreach
import ui.core.text.TextValue
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import org.scalajs.dom.{HTMLElement as DomHTMLElement}

final class Viewport extends AbstractComponent {
  val tagName = "div"

  private var contentHost: Div = uninitialized
  private val scheduledActions = new CompositeDisposable()
  private var nextConfId       = 0L

  val windows: ListProperty[Viewport.WindowConf]             = ListProperty()
  val overlays: ListProperty[Viewport.OverlayConf]           = ListProperty()
  val notifications: ListProperty[Viewport.NotificationConf] = ListProperty()

  override def compose(cursor: Cursor): Unit = {
    Viewport.ViewportContext.provide(this)(using this)
    addDisposable(Disposable(disposeState()))

    render(this, cursor) {
      addClass("scalajs-ui-viewport")

      contentHost = div {
        classes = Seq("scalajs-ui-viewport__content")

        style {
          minHeight = "100%"
        }
      }

      Foreach.foreach(windows) { conf =>
        Window.window(conf)
      }

      Foreach.foreach(overlays) { conf =>
        Overlay.render(conf)
      }

      div {
        classes = Seq("scalajs-ui-viewport-notification-host")

        Foreach.foreach(notifications) { conf =>
          Notification.notification(conf)
        }
      }
    }
  }

  private[viewport] def notifyProperty(
      message: ReadOnlyProperty[String],
      kind: Viewport.NotificationKind,
      durationMs: Int
  ): Viewport.NotificationConf = {
    val conf = new Viewport.NotificationConf(kind = kind)

    attach(conf, "notification")
    conf.message = message
    notifications += conf

    schedule(durationMs) {
      conf.visible.set(false)
    }
    schedule(durationMs + Viewport.notificationFadeOutMs) {
      removeNotification(conf)
    }

    conf
  }

  private[viewport] def closeNotification(conf: Viewport.NotificationConf): Unit =
    if (owns(conf)) {
      conf.visible.set(false)
      schedule(Viewport.notificationFadeOutMs) {
        removeNotification(conf)
      }
    }

  private[viewport] def addWindow(conf: Viewport.WindowConf): Viewport.WindowConf = {
    attach(conf, "window")
    if (!windows.exists(_ eq conf)) {
      placeWindow(conf)
      windows += conf
    }
    touchWindow(conf)
    conf
  }

  private def placeWindow(conf: Viewport.WindowConf, width: Double, height: Double): Unit = {
    val cascade = Viewport.windowBaseOffsetPx + windows.length * Viewport.windowStepPx
    visibleArea match {
      case Some(area) if conf.placement == Viewport.WindowPlacement.Centered =>
        val fittedWidth  = math.min(width, math.max(0.0, area.width - 2 * Viewport.windowMarginPx))
        val fittedHeight =
          math.min(height, math.max(0.0, area.height - 2 * Viewport.windowMarginPx))
        conf.leftPx.set(area.center(fittedWidth, horizontal = true))
        conf.topPx.set(area.center(fittedHeight, horizontal = false))
      case Some(area) =>
        conf.leftPx.set(area.place(cascade, width, horizontal = true))
        conf.topPx.set(area.place(cascade, height, horizontal = false))
      case None =>
        conf.leftPx.set(cascade)
        conf.topPx.set(cascade)
    }
  }

  private def placeWindow(conf: Viewport.WindowConf): Unit =
    placeWindow(conf, conf.widthProperty.get, conf.heightProperty.get)

  private[viewport] def showWindow(conf: Viewport.WindowConf): Unit =
    if (owns(conf)) {
      if (
        !conf.autoHeight && !conf.visible.get && conf.placement == Viewport.WindowPlacement.Centered && !conf.userPositioned
      )
        placeWindow(conf)
      else if (!conf.autoHeight) constrainWindow(conf)
      conf.visible.set(true)
      touchWindow(conf)
    }

  private[viewport] def hideWindow(conf: Viewport.WindowConf): Unit =
    if (owns(conf)) conf.visible.set(false)

  private[viewport] def repositionWindow(conf: Viewport.WindowConf): Unit =
    if (owns(conf) && conf.visible.get && conf.placement == Viewport.WindowPlacement.Centered)
      placeWindow(conf)

  private[viewport] def constrainWindow(conf: Viewport.WindowConf): Unit =
    constrainWindow(conf, conf.widthProperty.get, conf.heightProperty.get)

  private[viewport] def constrainWindow(
      conf: Viewport.WindowConf,
      width: Double,
      height: Double
  ): Unit =
    if (owns(conf)) visibleArea.foreach { area =>
      conf.leftPx.set(area.clamp(conf.leftPx.get, width, horizontal = true))
      conf.topPx.set(area.clamp(conf.topPx.get, height, horizontal = false))
    }

  private[viewport] def positionMeasuredWindow(
      conf: Viewport.WindowConf,
      width: Double,
      height: Double
  ): Unit =
    if (owns(conf)) {
      if (conf.placement == Viewport.WindowPlacement.Centered && !conf.userPositioned)
        placeWindow(conf, width, height)
      else constrainWindow(conf, width, height)
    }

  /** The part of this viewport currently visible in the browser window, in the coordinate space of
    * its absolutely positioned children (windows). Accounts for both the page scrolling past the
    * viewport and the viewport scrolling its own content. `None` outside the browser.
    */
  private def visibleArea: Option[Viewport.VisibleArea] =
    host match {
      case browser: DomHostElement =>
        browser.node match {
          case element: dom.HTMLElement =>
            val rect         = element.getBoundingClientRect()
            val clientLeft   = rect.left + element.clientLeft
            val clientTop    = rect.top + element.clientTop
            val root         = dom.document.documentElement
            val visibleLeft  = math.max(0.0, clientLeft)
            val visibleTop   = math.max(0.0, clientTop)
            val visibleRight = math.min(root.clientWidth.toDouble, clientLeft + element.clientWidth)
            val visibleBottom =
              math.min(root.clientHeight.toDouble, clientTop + element.clientHeight)
            Some(
              Viewport.VisibleArea(
                left = visibleLeft - clientLeft + element.scrollLeft,
                top = visibleTop - clientTop + element.scrollTop,
                width = math.max(0.0, visibleRight - visibleLeft),
                height = math.max(0.0, visibleBottom - visibleTop)
              )
            )
          case _ => None
        }
      case _ => None
    }

  private[viewport] def addOverlay(conf: Viewport.OverlayConf): Viewport.OverlayConf = {
    attach(conf, "overlay")
    if (!overlays.exists(_ eq conf)) overlays += conf
    conf
  }

  private[viewport] def closeOverlay(conf: Viewport.OverlayConf): Unit =
    if (owns(conf)) removeOverlay(conf)

  private[viewport] def closeOverlayById(id: String): Unit =
    overlays.find(_.id == id).foreach(closeOverlay)

  private[viewport] def closeWindow(conf: Viewport.WindowConf): Unit =
    if (owns(conf)) {
      conf.visible.set(false)
      schedule(Viewport.windowFadeOutMs) {
        removeWindow(conf)
      }
    }

  private[viewport] def closeWindowById(id: String): Unit =
    windows.find(_.id == id).foreach(closeWindow)

  private[viewport] def isActive(conf: Viewport.WindowConf): Boolean =
    owns(conf) && windows.forall(other => other.eq(conf) || other.zIndex.get < conf.zIndex.get)

  private[viewport] def touchWindow(conf: Viewport.WindowConf): Unit =
    if (owns(conf)) {
      var z = 0
      windows.foreach { current =>
        if (!current.eq(conf)) {
          current.zIndex.set(z)
          z += 1
        }
      }
      conf.zIndex.set(z)
    }

  private def uniqueId(prefix: String): String = {
    nextConfId += 1
    s"$prefix-$nextConfId"
  }

  private def attach(conf: Viewport.OwnedConf, prefix: String): Unit =
    conf.attachTo(this, uniqueId(prefix))

  private def owns(conf: Viewport.OwnedConf): Boolean =
    conf.ownerOption.contains(this)

  private def removeWindow(conf: Viewport.WindowConf): Unit = {
    val index = windows.indexWhere(_ eq conf)
    if (index >= 0) windows.remove(index)
    conf.detachFrom(this)
  }

  private def removeOverlay(conf: Viewport.OverlayConf): Unit = {
    val index = overlays.indexWhere(_ eq conf)
    if (index >= 0) overlays.remove(index)
    conf.detachFrom(this)
  }

  private def removeNotification(conf: Viewport.NotificationConf): Unit = {
    val index = notifications.indexWhere(_ eq conf)
    if (index >= 0) notifications.remove(index)
    conf.detachFrom(this)
  }

  private def schedule(delayMs: Int)(action: => Unit): Unit = {
    var pending      = true
    var cancellation = Disposable.empty
    val handle       = setTimeout(math.max(0, delayMs)) {
      if (pending) {
        pending = false
        scheduledActions.remove(cancellation)
        action
      }
    }
    cancellation = Disposable {
      if (pending) {
        pending = false
        clearTimeout(handle)
      }
    }
    scheduledActions.add(cancellation)
  }

  private def disposeState(): Unit = {
    scheduledActions.dispose()
    windows.toVector.foreach(_.detachFrom(this))
    overlays.toVector.foreach(_.detachFrom(this))
    notifications.toVector.foreach(_.detachFrom(this))
    windows.clear()
    overlays.clear()
    notifications.clear()
  }
}

object Viewport {

  private val ViewportContext: Context[Viewport] =
    Context.create[Viewport]("Viewport")

  enum NotificationKind(val cssClass: String) {
    case Info    extends NotificationKind("scalajs-ui-viewport-notification--info")
    case Success extends NotificationKind("scalajs-ui-viewport-notification--success")
    case Warning extends NotificationKind("scalajs-ui-viewport-notification--warning")
    case Error   extends NotificationKind("scalajs-ui-viewport-notification--error")
  }

  type WindowBody  = AbstractComponent ?=> Cursor ?=> Unit
  type OverlayBody = Overlay ?=> Cursor ?=> Unit

  enum WindowPlacement     { case Cascaded, Centered }
  enum WindowCloseBehavior { case Remove, Hide       }

  private val notificationFadeOutMs = 250
  private val windowFadeOutMs       = 300
  private val windowBaseOffsetPx    = 72.0
  private val windowStepPx          = 28.0
  private val windowMarginPx        = 8.0

  private[viewport] final case class VisibleArea(
      left: Double,
      top: Double,
      width: Double,
      height: Double
  ) {

    /** Position along one axis: `offset` into the visible area, pulled back so a window of `size`
      * still fits, but never before the area's leading margin.
      */
    def place(offset: Double, size: Double, horizontal: Boolean): Double = {
      val (start, extent) = if (horizontal) (left, width) else (top, height)
      val maxOffset       = math.max(windowMarginPx, extent - size - windowMarginPx)
      start + math.min(offset, maxOffset)
    }

    def center(size: Double, horizontal: Boolean): Double = {
      val (start, extent) = if (horizontal) (left, width) else (top, height)
      start + math.max(windowMarginPx, (extent - size) / 2)
    }

    def clamp(position: Double, size: Double, horizontal: Boolean): Double = {
      val (start, extent) = if (horizontal) (left, width) else (top, height)
      val minimum         = start + windowMarginPx
      val maximum         = start + math.max(windowMarginPx, extent - size - windowMarginPx)
      position.max(minimum).min(maximum)
    }
  }

  private[viewport] trait OwnedConf {
    private var owner: Viewport | Null    = null
    private var assignedId: String | Null = null

    final def id: String =
      Option(assignedId).getOrElse {
        throw new IllegalStateException("Viewport configuration has not been registered yet.")
      }

    private[viewport] final def ownerOption: Option[Viewport] = Option(owner)

    private[viewport] final def attachTo(viewport: Viewport, id: String): Unit =
      owner match {
        case null =>
          owner = viewport
          assignedId = id
        case current if current eq viewport => ()
        case _                              =>
          throw new IllegalStateException(
            "Viewport configuration is already registered with another Viewport."
          )
      }

    private[viewport] final def detachFrom(viewport: Viewport): Unit =
      if (owner eq viewport) {
        owner = null
        disposeBindings()
      }

    protected def disposeBindings(): Unit = ()
  }

  final class NotificationConf(
      val kind: NotificationKind = NotificationKind.Info
  ) extends OwnedConf {
    val messageProperty: Property[String]  = Property("")
    val visible: Property[Boolean]         = Property(true)
    private var messageBinding: Disposable = Disposable.empty

    def message: ReadOnlyProperty[String] =
      messageProperty

    def message_=(value: String): Unit = {
      messageBinding.dispose()
      messageBinding = Disposable.empty
      messageProperty.set(Option(value).getOrElse(""))
    }

    def message_=(value: ReadOnlyProperty[String]): Unit = {
      messageBinding.dispose()
      messageBinding = value.observe(messageProperty.set)
    }

    override protected def disposeBindings(): Unit = messageBinding.dispose()
  }

  final class WindowConf(
      val body: WindowBody,
      val widthPx: Int = 520,
      val heightPx: Int = 360,
      val leftPx: Property[Double] = Property(windowBaseOffsetPx),
      val topPx: Property[Double] = Property(windowBaseOffsetPx),
      val zIndex: Property[Int] = Property(0),
      val visible: Property[Boolean] = Property(true),
      val onClose: Option[Window => Unit] = None,
      val onClick: Option[Window => Unit] = None,
      val resizable: Boolean = true,
      val placement: WindowPlacement = WindowPlacement.Cascaded,
      val mobileSheet: Boolean = true,
      val closeBehavior: WindowCloseBehavior = WindowCloseBehavior.Remove,
      val autoHeight: Boolean = false
  ) extends OwnedConf {
    private[viewport] var userPositioned: Boolean = false
    val widthProperty: Property[Double]           = Property(widthPx.toDouble)
    val heightProperty: Property[Double]          = Property(heightPx.toDouble)
    val titleProperty: Property[String]           = Property("")
    private var titleBinding: Disposable          = Disposable.empty

    def title: ReadOnlyProperty[String] =
      titleProperty

    def title_=(value: String): Unit = {
      titleBinding.dispose()
      titleBinding = Disposable.empty
      titleProperty.set(Option(value).getOrElse(""))
    }

    def title_=(value: ReadOnlyProperty[String]): Unit = {
      titleBinding.dispose()
      titleBinding = value.observe(titleProperty.set)
    }

    override protected def disposeBindings(): Unit = titleBinding.dispose()
  }

  final class OverlayConf(
      val anchor: Option[DomHTMLElement],
      val body: OverlayBody,
      val widthPx: Option[Double],
      val effectiveWidthProperty: Property[Double],
      val offsetXPx: Double = 0.0,
      val offsetYPx: Double = 4.0,
      val minWidthPx: Option[Double] = None,
      val maxHeightPx: Option[Double] = None,
      val marginViewportPx: Double = 8.0,
      val flipY: Boolean = true,
      val zIndex: Int = 90000
  ) extends OwnedConf

  object WindowConf {
    def apply(
        title: String,
        widthPx: Int = 520,
        heightPx: Int = 360,
        onClose: Option[Window => Unit] = None,
        onClick: Option[Window => Unit] = None,
        resizable: Boolean = true,
        placement: WindowPlacement = WindowPlacement.Cascaded,
        mobileSheet: Boolean = true,
        closeBehavior: WindowCloseBehavior = WindowCloseBehavior.Remove,
        autoHeight: Boolean = false
    )(body: WindowBody): WindowConf = {
      val conf = new WindowConf(
        body = body,
        widthPx = widthPx,
        heightPx = heightPx,
        resizable = resizable,
        onClose = onClose,
        onClick = onClick,
        placement = placement,
        mobileSheet = mobileSheet,
        closeBehavior = closeBehavior,
        autoHeight = autoHeight
      )
      conf.title = title
      conf
    }
  }

  def viewport(
      body: AbstractComponent ?=> Viewport ?=> Cursor ?=> Unit = {}
  )(using parent: AbstractComponent, cursor: Cursor): Viewport = {
    val mounted = Runtime.mount(new Viewport(), cursor, Some(parent))

    renderInto(mounted.contentHost) {
      body(using mounted.contentHost)(using mounted)
    }

    mounted
  }

  def current(using component: AbstractComponent): Option[Viewport] =
    ViewportContext.inject

  def requireCurrent(using component: AbstractComponent): Viewport =
    current.getOrElse {
      throw new IllegalStateException("No Viewport found in the current component tree.")
    }

  def windows(using component: AbstractComponent): ListProperty[WindowConf] =
    requireCurrent.windows

  def overlays(using component: AbstractComponent): ListProperty[OverlayConf] =
    requireCurrent.overlays

  def notifications(using component: AbstractComponent): ListProperty[NotificationConf] =
    requireCurrent.notifications

  def notify(
      message: String,
      kind: NotificationKind = NotificationKind.Info,
      durationMs: Int = 3000
  )(using component: AbstractComponent): NotificationConf =
    requireCurrent.notifyProperty(Property(Option(message).getOrElse("")), kind, durationMs)

  def notify[T](
      message: T,
      kind: NotificationKind,
      durationMs: Int
  )(using textValue: TextValue[T], component: AbstractComponent): NotificationConf =
    requireCurrent.notifyProperty(textValue.asReadOnlyProperty(message), kind, durationMs)

  def closeNotification(conf: NotificationConf): Unit =
    conf.ownerOption.foreach(_.closeNotification(conf))

  def addWindow(conf: WindowConf)(using component: AbstractComponent): WindowConf =
    requireCurrent.addWindow(conf)

  def addOverlay(conf: OverlayConf)(using component: AbstractComponent): OverlayConf =
    requireCurrent.addOverlay(conf)

  def closeOverlay(conf: OverlayConf): Unit =
    conf.ownerOption.foreach(_.closeOverlay(conf))

  def closeOverlayById(id: String)(using component: AbstractComponent): Unit =
    requireCurrent.closeOverlayById(id)

  def addWindow(
      title: String,
      widthPx: Int = 520,
      heightPx: Int = 360
  )(body: WindowBody)(using component: AbstractComponent): WindowConf =
    addWindow(WindowConf(title, widthPx, heightPx)(body))

  def addWindow(
      title: String,
      widthPx: Int,
      heightPx: Int,
      resizable: Boolean
  )(body: WindowBody)(using component: AbstractComponent): WindowConf =
    addWindow(WindowConf(title, widthPx, heightPx, resizable = resizable)(body))

  def addWindow[T](
      title: T,
      widthPx: Int,
      heightPx: Int
  )(body: WindowBody)(using textValue: TextValue[T], component: AbstractComponent): WindowConf = {
    val conf = new WindowConf(body, widthPx, heightPx)
    conf.title_=(textValue.asReadOnlyProperty(title))
    addWindow(conf)
  }

  def closeWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.closeWindow(conf))

  /** Reuse a window without mounting another copy of its contents. */
  def showWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.showWindow(conf))

  /** Hide a reusable window without disposing its contents. */
  def hideWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.hideWindow(conf))

  /** Keep a centered window within the currently visible area after a browser resize. */
  def repositionWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.repositionWindow(conf))

  private[viewport] def constrainWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.constrainWindow(conf))

  private[viewport] def positionMeasuredWindow(
      conf: WindowConf,
      width: Double,
      height: Double
  ): Unit =
    conf.ownerOption.foreach(_.positionMeasuredWindow(conf, width, height))

  def closeWindowById(id: String)(using component: AbstractComponent): Unit =
    requireCurrent.closeWindowById(id)

  def isActive(conf: WindowConf): Boolean =
    conf.ownerOption.exists(_.isActive(conf))

  def touchWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.touchWindow(conf))
}
