package ui.router

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.i18n.I18nRuntime
import ui.core.layout.Anchor
import ui.core.render.{Cursor, UiEvent}

import scala.scalajs.js

object RouterLink {

  def routerLink()(
      body: Anchor ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Anchor = {
    val link = new Anchor()
    link.addClass("ui-link")

    DslLayer.child(link) {
      body
      // The href is the body's; whether it is internal is decided at click time.
      handler(using link).foreach(handler => installNavigation(link, handler.navigate))
    }
  }

  def routerLink(
      to: String,
      activeClass: String = "active"
  )(
      body: Anchor ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Anchor = {
    val link = new Anchor()
    link.addClass("ui-link")

    DslLayer.child(link) {
      handler(using link) match {
        case Some(handler) if isAppPath(to) =>
          link.href = handler.hrefForAppPath(to)
          // The locale is part of the href. A link outside the outlet outlives the navigation
          // that switches it, so it follows the locale rather than keeping the one it was composed
          // with -- which a click would otherwise reinstate.
          handler.locale.foreach { locale =>
            link.addDisposable(locale.observeWithoutInitial { _ =>
              link.href = handler.hrefForAppPath(to)
            })
          }
          link.classCondition(
            activeClass,
            handler.currentPath.map(defaultActiveMatcher(handler.appPathFor(to)))
          )
          body
          installNavigation(link, handler.navigate)

        case _ =>
          link.href = to
          body
      }
    }
  }

  private def installNavigation(link: Anchor, navigate: String => Unit): Unit =
    link.onClickHandler { event =>
      val destination = link.href

      if (isPlainLeftClick(event) && isInternalDestination(destination) && link.target.isEmpty) {
        event.preventDefault()
        navigate(destination)
      }
    }

  /** Whether a click is the router's to handle: left button, no modifier, default not already
    * prevented. A modifier or another button asks the browser for a new tab or window; a prevented
    * default was handled by someone else. Read as properties rather than as a `MouseEvent`: the
    * test host dispatches without a browser, and the properties are what decides, not the class.
    */
  private def isPlainLeftClick(event: UiEvent): Boolean =
    event.raw match {
      case raw: js.Object =>
        val click = raw.asInstanceOf[js.Dynamic]

        def flag(name: String): Boolean =
          click.selectDynamic(name).asInstanceOf[js.UndefOr[Boolean]].contains(true)

        click.selectDynamic("button").asInstanceOf[js.UndefOr[Int]].forall(_ == 0) &&
        !flag("ctrlKey") && !flag("metaKey") && !flag("shiftKey") && !flag("altKey") &&
        !flag("defaultPrevented")

      case _ =>
        true
    }

  private def handler(using link: Anchor): Option[RouterLinkHandler] =
    RouterLinkHandler.inject(using link).orElse(routerHandler(using link))

  private def routerHandler(using link: Anchor): Option[RouterLinkHandler] =
    Router.current(using link).map { router =>
      RouterLinkHandler(
        navigate = path => router.navigate(path),
        currentPath = router.state.map(_.path),
        hrefForAppPath = path => router.hrefFor(path),
        appPathFor = path => router.appPathFor(path),
        locale = I18nRuntime.current(using link).map(_.locale)
      )
    }

  private def isInternalDestination(destination: String): Boolean =
    destination.startsWith("/")

  private def isAppPath(destination: String): Boolean =
    destination.startsWith("/") && !destination.startsWith("//")

  private def defaultActiveMatcher(normalized: String): String => Boolean =
    currentPath =>
      if (normalized == "/") currentPath == "/"
      else currentPath == normalized || currentPath.startsWith(s"$normalized/")
}
