package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Anchor.*
import ui.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import ui.core.state.{Disposable, Property}
import ui.router.RouterLink.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

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
}

