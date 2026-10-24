package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.i18n.I18nLocale
import ui.core.layout.Anchor.*
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import ui.core.state.{Disposable, Property}
import ui.router.RouterLink.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class RouterLinkSpec extends AnyFlatSpec with Matchers {

  "RouterLink" should "resolve application paths, track the active route and navigate on click" in {
    val currentPath = Property("/users/42")
    val navigations = mutable.ArrayBuffer.empty[String]
    val handler     = RouterLinkHandler(
      navigate = navigations += _,
      currentPath = currentPath,
      hrefForAppPath = path => s"/app/de$path"
    )
    var linkHost: LinkTestHostElement = null
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") linkHost = host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          routerLink("/users", activeClass = "selected") {}
      },
      cursor
    )

    linkHost.attribute("href") shouldBe Some("/app/de/users")
    linkHost.classNames should contain("selected")

    currentPath.set("/settings")
    linkHost.classNames should not contain "selected"

    linkHost.fireClick() shouldBe true
    navigations.toSeq shouldBe Seq("/app/de/users")

    Runtime.unmount(root)
    currentPath.set("/users")

    linkHost.classNames should not contain "selected"
    linkHost.hasListener("click") shouldBe false
  }

  it should "leave external destinations to the browser" in {
    val handler = RouterLinkHandler(
      navigate = _ => fail("external links must not use router navigation"),
      currentPath = Property("/"),
      hrefForAppPath = path => s"/app$path"
    )
    var linkHost: LinkTestHostElement = null
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") linkHost = host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          routerLink("https://example.test") {
            target = "_blank"
            rel = "noopener noreferrer"
          }
      },
      cursor
    )

    linkHost.attribute("href") shouldBe Some("https://example.test")
    linkHost.attribute("target") shouldBe Some("_blank")
    linkHost.attribute("rel") shouldBe Some("noopener noreferrer")
    linkHost.hasListener("click") shouldBe false

    Runtime.unmount(root)
  }

  it should "use the router's locale resolver for its active state" in {
    val handler = RouterLinkHandler(
      navigate = _ => (),
      currentPath = Property("/articles"),
      hrefForAppPath = identity,
      appPathFor = path => path.stripPrefix("/fr")
    )
    var linkHost: LinkTestHostElement = null
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") linkHost = host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          routerLink("/fr/articles", activeClass = "selected") {}
      },
      cursor
    )

    linkHost.classNames should contain("selected")

    Runtime.unmount(root)
  }

  it should "leave a click with a modifier or another button to the browser" in {
    val navigations = mutable.ArrayBuffer.empty[String]
    val handler     = RouterLinkHandler(
      navigate = navigations += _,
      currentPath = Property("/"),
      hrefForAppPath = identity
    )
    var linkHost: LinkTestHostElement = null
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") linkHost = host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          routerLink("/users") {}
      },
      cursor
    )

    // Ctrl/Cmd+click, Shift+click, middle button, and a click someone else already handled.
    linkHost.fireClick(js.Dynamic.literal(button = 0, ctrlKey = true)) shouldBe false
    linkHost.fireClick(js.Dynamic.literal(button = 0, metaKey = true)) shouldBe false
    linkHost.fireClick(js.Dynamic.literal(button = 0, shiftKey = true)) shouldBe false
    linkHost.fireClick(js.Dynamic.literal(button = 0, altKey = true)) shouldBe false
    linkHost.fireClick(js.Dynamic.literal(button = 1)) shouldBe false
    linkHost.fireClick(js.Dynamic.literal(button = 0, defaultPrevented = true)) shouldBe false
    navigations shouldBe empty

    linkHost.fireClick(js.Dynamic.literal(button = 0)) shouldBe true
    navigations.toSeq shouldBe Seq("/users")

    Runtime.unmount(root)
  }

  it should "follow the locale in its href after it was composed" in {
    val locale  = Property(I18nLocale.En)
    val handler = RouterLinkHandler(
      navigate = _ => (),
      currentPath = Property("/"),
      hrefForAppPath = path => s"/${locale.get.code}$path",
      locale = Some(locale)
    )
    var linkHost: LinkTestHostElement = null
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") linkHost = host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          routerLink("/users") {}
      },
      cursor
    )

    linkHost.attribute("href") shouldBe Some("/en/users")

    locale.set(I18nLocale("de"))

    linkHost.attribute("href") shouldBe Some("/de/users")

    Runtime.unmount(root)
    locale.set(I18nLocale.En)

    linkHost.attribute("href") shouldBe Some("/de/users")
  }

  it should "navigate in the application for a body-provided internal href" in {
    val navigations = mutable.ArrayBuffer.empty[String]
    val handler     = RouterLinkHandler(
      navigate = navigations += _,
      currentPath = Property("/"),
      hrefForAppPath = identity
    )
    val hosts  = mutable.ArrayBuffer.empty[LinkTestHostElement]
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") hosts += host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit = {
          routerLink() { href = "/users/7"; text("User 7") {} }
          routerLink() { href = "https://example.test"; text("Elsewhere") {} }
        }
      },
      cursor
    )

    hosts.head.fireClick() shouldBe true
    navigations.toSeq shouldBe Seq("/users/7")

    hosts.last.fireClick() shouldBe false
    navigations.toSeq shouldBe Seq("/users/7")

    Runtime.unmount(root)
  }
}
