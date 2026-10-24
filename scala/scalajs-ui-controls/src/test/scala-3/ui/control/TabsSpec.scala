package ui.control

import ui.control.tabs.Tabs
import ui.control.tabs.Tabs.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import ui.core.state.{Disposable, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class TabsSpec extends AnyFlatSpec with Matchers {

  "Tabs" should "render accessible triggers and only the active panel during SSR" in {
    val html = renderTabs {
      tabs {
        tab("Location") {
          div { text("Current location panel") {} }
        }
        tab("Chat") {
          div { text("Chat panel") {} }
        }
      }
    }

    html should include("class=\"ui-tabs\"")
    html should include("role=\"tablist\"")
    html should include("role=\"tab\"")
    html should include("aria-selected=\"true\"")
    html should include("tabindex=\"-1\"")
    html should include("Current location panel")
    html should not include "Chat panel"
    // Ids are allocated in the browser after hydration; the server must not promise any.
    html should not include "aria-controls"
    html should not include "aria-labelledby"
  }

  it should "keep inactive panels mounted and hidden when configured" in {
    var locationRenders = 0
    var chatRenders     = 0
    var control: Tabs   = null
    val cursor          = new SsrCursor()

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            renderMode = RenderMode.KeepMountedHidden
            tab("Location") {
              locationRenders += 1
              div { text("Current location panel") {} }
            }
            tab("Chat") {
              chatRenders += 1
              div { text("Chat panel") {} }
            }
          }
      },
      cursor
    )

    cursor.collectHtml() should include("Current location panel")
    cursor.collectHtml() should include("Chat panel")
    cursor.collectHtml() should include("display: none")
    // The visible panel has no display property at all, not an empty one.
    cursor.collectHtml() should not include "style=\"display: \""
    locationRenders shouldBe 1
    chatRenders shouldBe 1

    control.setSelectedIndex(1)

    locationRenders shouldBe 1
    chatRenders shouldBe 1
    cursor.collectHtml() should include("aria-hidden=\"false\"")

    Runtime.unmount(root)
  }

  it should "replace active content and normalize selection across list mutations" in {
    val firstTitle    = Property("First")
    val cursor        = new SsrCursor()
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab(firstTitle) {
              div { text("First panel") {} }
            }
            tab("Second") {
              div { text("Second panel") {} }
            }
          }
      },
      cursor
    )

    control.setSelectedIndex(1)
    cursor.collectHtml() should include("Second panel")
    cursor.collectHtml() should not include "First panel"

    control.tabsProperty.remove(1)
    control.getSelectedIndex shouldBe 0
    cursor.collectHtml() should include("First panel")
    cursor.collectHtml() should not include "Second panel"

    firstTitle.set("Renamed")
    cursor.collectHtml() should include("Renamed")

    Runtime.unmount(root)
    val disposedHtml = cursor.collectHtml()
    firstTitle.set("Ignored")
    control.tabsProperty.clear()

    cursor.collectHtml() shouldBe disposedHtml
  }

  it should "switch render modes through the public property" in {
    val cursor        = new SsrCursor()
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab("One") { div { text("Panel one") {} } }
            tab("Two") { div { text("Panel two") {} } }
          }
      },
      cursor
    )

    cursor.collectHtml() should not include "Panel two"

    control.renderModeProperty.set(RenderMode.KeepMountedHidden)
    cursor.collectHtml() should include("Panel one")
    cursor.collectHtml() should include("Panel two")

    control.setSelectedIndex(1)
    control.renderModeProperty.set(RenderMode.ActiveOnly)
    cursor.collectHtml() should include("Panel two")
    cursor.collectHtml() should not include "Panel one"

    Runtime.unmount(root)
  }

  it should "support click and keyboard selection and remove handlers on unmount" in {
    val hosts         = mutable.ArrayBuffer.empty[TabsTestHostElement]
    val cursor        = new TabsEventCursor(hosts += _)
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab("One") { div {} }
            tab("Two") { div {} }
          }
      },
      cursor
    )

    val tabsHost = hosts.find(host => host.tagName == "section").get
    val tablist  = hosts.find(_.attribute("role").contains("tablist")).get
    val triggers = hosts.filter(_.tagName == "button")

    triggers(1).fire("click") shouldBe false
    control.getSelectedIndex shouldBe 1

    // Keys are handled on the tablist, where only the triggers take focus. A key pressed inside
    // panel content bubbles to the section, which has no listener to take it away.
    tabsHost.hasListener("keydown") shouldBe false
    tablist.fire("keydown", js.Dynamic.literal(key = "ArrowLeft")) shouldBe true
    control.getSelectedIndex shouldBe 0

    tablist.fire("keydown", js.Dynamic.literal(key = "End")) shouldBe true
    control.getSelectedIndex shouldBe 1

    // Shortcuts, composition and already handled keys belong to someone else.
    tablist.fire("keydown", js.Dynamic.literal(key = "ArrowLeft", ctrlKey = true)) shouldBe false
    tablist.fire("keydown", js.Dynamic.literal(key = "Home", metaKey = true)) shouldBe false
    tablist.fire("keydown", js.Dynamic.literal(key = "Home", isComposing = true)) shouldBe false
    tablist.fire(
      "keydown",
      js.Dynamic.literal(key = "Home", defaultPrevented = true)
    ) shouldBe false
    control.getSelectedIndex shouldBe 1

    Runtime.unmount(root)

    tablist.hasListener("keydown") shouldBe false
    triggers.foreach(_.hasListener("click") shouldBe false)
  }

  it should "pair triggers and panels through ids allocated after hydration" in {
    val hosts         = mutable.ArrayBuffer.empty[TabsTestHostElement]
    val cursor        = new TabsEventCursor(hosts += _, browser = true, deferredHydration = true)
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit = {
          control = tabs {
            renderMode = RenderMode.KeepMountedHidden
            tab("One") { div {} }
            tab("Two") { div {} }
          }
          tabs {
            tab("Other") { div {} }
          }
        }
      },
      cursor
    )

    val triggers = hosts.filter(_.tagName == "button")
    def panels   = hosts.filter(_.attribute("role").contains("tabpanel"))

    // Hydration claims the server markup, which carries no ids.
    triggers.foreach(_.attribute("id") shouldBe None)
    triggers.foreach(_.attribute("aria-controls") shouldBe None)
    panels.foreach(_.attribute("aria-labelledby") shouldBe None)

    cursor.completeHydration()

    triggers.zip(panels).foreach { (trigger, panel) =>
      trigger.attribute("id").get should startWith("ui-tabs-")
      trigger.attribute("aria-controls") shouldBe panel.attribute("id")
      panel.attribute("aria-labelledby") shouldBe trigger.attribute("id")
    }
    // Two instances on one page never share an id.
    triggers.map(_.attribute("id").get).distinct.size shouldBe 3

    // A panel mounted later joins the same scheme.
    control.setSelectedIndex(1)
    control.renderModeProperty.set(RenderMode.ActiveOnly)
    panels.last.attribute("aria-labelledby") shouldBe triggers(1).attribute("id")
    triggers(1).attribute("aria-controls") shouldBe panels.last.attribute("id")

    Runtime.unmount(root)
  }

  it should "clamp a reactively bound selected index" in {
    val selected      = Property(99)
    val cursor        = new SsrCursor()
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab("One") { div {} }
            tab("Two") { div {} }
            selectedIndex = selected
          }
      },
      cursor
    )

    control.getSelectedIndex shouldBe 1

    selected.set(-5)
    control.getSelectedIndex shouldBe 0

    Runtime.unmount(root)
  }

  private def renderTabs(body: AbstractComponent ?=> Cursor ?=> Unit): String =
    Runtime.renderToString { cursor =>
      Runtime.mount(
        new TabsTestRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            body
        },
        cursor
      )
    }
}
