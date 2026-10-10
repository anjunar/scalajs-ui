package ui.control.carousel

import ui.control.carousel.Carousel.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import ui.core.state.{Disposable, ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class CarouselSpec extends AnyFlatSpec with Matchers {

  "Carousel navigation" should "wrap or clamp through its public state" in {
    val items                     = ListProperty[String](js.Array("One", "Two", "Three"))
    val cursor                    = new SsrCursor()
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = carousel[String] {
            Carousel.items = items
            Carousel.activeIndex = 2
          }
      },
      cursor
    )

    control.next()
    control.activeIndexProperty.get shouldBe 0
    control.currentItem shouldBe Some("One")

    control.previous()
    control.activeIndexProperty.get shouldBe 2
    control.currentItem shouldBe Some("Three")

    control.wrapAroundProperty.set(false)
    control.next()
    control.activeIndexProperty.get shouldBe 2
    control.goTo(-10)
    control.activeIndexProperty.get shouldBe 0

    Runtime.unmount(root)
  }

  "Carousel SSR" should "render every slide and mark the active state by default" in {
    val items = ListProperty[String](js.Array("One", "Two", "Three"))

    val html = renderCarousel {
      carousel[String] {
        Carousel.items = items
        Carousel.activeIndex = 1
        slideRenderer = (item: String, index: Int) => div { text(s"$index:$item") {} }
      }
    }

    html should include("ui-carousel--ssr-all-states")
    html should include("aria-roledescription=\"carousel\"")
    html should include("aria-roledescription=\"slide\"")
    html should include("0:One")
    html should include("1:Two")
    html should include("2:Three")
    html should include("2 / 3")
    html should include("aria-current=\"true\"")
    html should include("is-previous")
    html should include("is-next")
    html should include("inert=\"\"")
  }

  it should "mount only the active slide in both sides of active-only mode" in {
    val items = ListProperty[String](js.Array("One", "Two", "Three"))

    val html = renderCarousel {
      carousel[String] {
        Carousel.items = items
        Carousel.activeIndex = 1
        Carousel.ssrShowAllStates = false
        slideRenderer = (item: String, index: Int) => div { text(s"$index:$item") {} }
      }
    }

    html should not include "ui-carousel--ssr-all-states"
    html should not include "0:One"
    html should include("1:Two")
    html should not include "2:Three"
    html should not include "ui-carousel--stage"
  }

  "Carousel list lifecycle" should "follow mutations, replacement and render-mode changes" in {
    val first                     = ListProperty[String](js.Array("One", "Two", "Three"))
    val second                    = ListProperty[String](js.Array("Alpha", "Beta"))
    val cursor                    = new SsrCursor()
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = carousel[String] {
            Carousel.items = first
            Carousel.slideRenderer = (item: String, index: Int) => div { text(s"$index:$item") {} }
          }
      },
      cursor
    )

    control.goTo(2)
    first.remove(2)
    control.activeIndexProperty.get shouldBe 0
    control.currentItem shouldBe Some("One")
    cursor.collectHtml() should not include "Three"

    control.ssrShowAllStatesProperty.set(false)
    control.goTo(1)
    cursor.collectHtml() should include("1:Two")
    cursor.collectHtml() should not include "0:One"

    control.setItems(second)
    control.currentItem shouldBe Some("Beta")
    cursor.collectHtml() should include("1:Beta")
    cursor.collectHtml() should not include "Two"

    Runtime.unmount(root)
    val detachedHtml = cursor.collectHtml()
    second.addOne("Gamma")
    control.activeIndexProperty.set(0)
    cursor.collectHtml() shouldBe detachedHtml
  }

  "Carousel interaction" should "handle buttons and keyboard and remove handlers on unmount" in {
    val hosts                     = mutable.ArrayBuffer.empty[CarouselTestHostElement]
    val cursor                    = new CarouselEventCursor(hosts += _, browser = true)
    val items                     = ListProperty[String](js.Array("One", "Two", "Three"))
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = carousel[String] {
            Carousel.items = items
          }
      },
      cursor
    )

    val carouselHost    = hosts.find(_.tagName == "section").get
    val secondIndicator = hosts.find(_.attribute("aria-label").contains("Go to slide 2")).get
    val nextButton      = hosts.find(_.attribute("aria-label").contains("Next slide")).get
    val firstSlide      = hosts.find(_.attribute("data-slide-index").contains("0")).get
    val secondSlide     = hosts.find(_.attribute("data-slide-index").contains("1")).get
    val thirdSlide      = hosts.find(_.attribute("data-slide-index").contains("2")).get

    carouselHost.attribute("class").get should include("ui-carousel--stage")
    firstSlide.attribute("class").get should include("is-active")
    secondSlide.attribute("class").get should include("is-next")
    secondSlide.attribute("inert") shouldBe Some("")

    secondIndicator.fire("click") shouldBe false
    control.activeIndexProperty.get shouldBe 1
    secondSlide.attribute("class").get should include("is-active")
    secondSlide.attribute("inert") shouldBe None
    firstSlide.attribute("class").get should include("is-previous")

    nextButton.fire("click") shouldBe false
    control.activeIndexProperty.get shouldBe 2

    carouselHost.fire("keydown", js.Dynamic.literal(key = "Home")) shouldBe true
    control.activeIndexProperty.get shouldBe 0

    control.wrapAroundProperty.set(false)
    thirdSlide.attribute("class").get should not include "is-previous"
    secondSlide.attribute("class").get should include("is-next")

    Runtime.unmount(root)
    carouselHost.hasListener("keydown") shouldBe false
    secondIndicator.hasListener("click") shouldBe false
    nextButton.hasListener("click") shouldBe false
  }

  "Carousel side previews" should "show one to three unique neighbors per side" in {
    val hosts  = mutable.ArrayBuffer.empty[CarouselTestHostElement]
    val cursor = new CarouselEventCursor(hosts += _, browser = true)
    val items  = ListProperty[String](js.Array("0", "1", "2", "3", "4", "5", "6"))
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = carousel[String] {
            Carousel.items = items
            Carousel.sidePreviewCount = 2
          }
      },
      cursor
    )

    val carouselHost                               = hosts.find(_.tagName == "section").get
    def slide(index: Int): CarouselTestHostElement =
      hosts.find(_.attribute("data-slide-index").contains(index.toString)).get
    def hasClass(index: Int, name: String): Boolean =
      slide(index).attribute("class").exists(_.split(" ").contains(name))

    carouselHost.attribute("class").get should include("ui-carousel--side-previews-2")
    carouselHost.attribute("class").get should not include "ui-carousel--side-previews-3"
    hasClass(6, "is-previous") shouldBe true
    hasClass(5, "is-previous-2") shouldBe true
    hasClass(1, "is-next") shouldBe true
    hasClass(2, "is-next-2") shouldBe true
    hasClass(3, "is-next-3") shouldBe false

    control.setSidePreviewCount(3)
    carouselHost.attribute("class").get should include("ui-carousel--side-previews-3")
    hasClass(4, "is-previous-3") shouldBe true
    hasClass(3, "is-next-3") shouldBe true

    control.wrapAroundProperty.set(false)
    hasClass(6, "is-previous") shouldBe false
    hasClass(5, "is-previous-2") shouldBe false
    hasClass(3, "is-next-3") shouldBe true

    control.setSidePreviewCount(99)
    control.sidePreviewCountProperty.get shouldBe 3
    control.setSidePreviewCount(0)
    control.sidePreviewCountProperty.get shouldBe 1
    hasClass(2, "is-next-2") shouldBe false

    Runtime.unmount(root)
  }

  "Carousel autoplay" should "restart its browser timer and dispose it with the component" in {
    val scheduler                 = new TestIntervalScheduler
    val hosts                     = mutable.ArrayBuffer.empty[CarouselTestHostElement]
    val cursor                    = new CarouselEventCursor(hosts += _, browser = true)
    val items                     = ListProperty[String](js.Array("One", "Two", "Three"))
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using parent: AbstractComponent, cursor: Cursor): Unit =
          control = DslLayer.child(
            new Carousel[String](
              (current: Carousel[String]) ?=>
                (_: Cursor) ?=> {
                  current.setItems(items)
                  current.autoAdvanceMsProperty.set(2500)
                },
              scheduler
            )
          ) {}
      },
      cursor
    )

    scheduler.scheduledIntervals shouldBe Seq(2500)
    scheduler.tick()
    control.activeIndexProperty.get shouldBe 1

    control.autoAdvanceMsProperty.set(900)
    scheduler.scheduledIntervals shouldBe Seq(2500, 900)
    scheduler.disposedCount shouldBe 1

    items.clear()
    scheduler.disposedCount shouldBe 2

    Runtime.unmount(root)
    scheduler.activeTaskCount shouldBe 0
  }

  private def renderCarousel(body: AbstractComponent ?=> Cursor ?=> Unit): String =
    Runtime.renderToString { cursor =>
      Runtime.mount(
        new CarouselTestRoot {
          override protected def content(using AbstractComponent, Cursor): Unit = body
        },
        cursor
      )
    }
}
