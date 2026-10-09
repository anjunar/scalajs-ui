package app.pages

import app.components.Showcase.*
import ui.control.carousel.Carousel
import ui.control.carousel.Carousel.*
import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.ariaPressed
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.EventDsl.onClick
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Button.button
import ui.core.layout.Div.div
import ui.core.layout.HBox.hbox
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.state.ListProperty
import ui.core.i18n.i18n

object CarouselPage {

  final case class SlideCard(kicker: String, title: String, copy: String)

  private val showcaseSlides = Seq(
    SlideCard(
      "Atlas",
      "Architecture that keeps moving",
      "The carousel owns the active state while the slide renderer stays declarative."
    ),
    SlideCard(
      "Signal",
      "Auto-advance without hidden magic",
      "A lifecycle-bound timer rotates the same explicit active-index property."
    ),
    SlideCard(
      "Northwind",
      "SSR can surface every state",
      "Stable dynamic ranges keep the server and hydration structure aligned."
    ),
    SlideCard(
      "Harbor",
      "Wrap-around is part of the contract",
      "The next step after the right edge returns to the beginning."
    ),
    SlideCard(
      "Keystone",
      "Every view has its place",
      "The stage keeps nearby slides in sight without changing the active item."
    ),
    SlideCard(
      "Pulse",
      "A responsive rhythm",
      "Choose how many neighboring slides remain visible on either side."
    ),
    SlideCard(
      "Orbit",
      "A complete loop",
      "Seven slides make all three preview levels visible at once."
    )
  )

  def render()(using AbstractComponent, Cursor): Unit = {
    val slides = ListProperty[SlideCard]()
    slides.setAll(showcaseSlides)

    showcasePage(i18n"Carousel", i18n"Looping slides with explicit state and stable SSR.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Sequenced content",
          i18n"One active slide, one lifecycle",
          i18n"Navigation, indicators, keyboard input and autoplay all update the same reactive selection."
        )

        metricStrip(
          i18n"Looping"    -> i18n"Next after the last slide starts at the beginning.",
          i18n"Autoplay"   -> i18n"A positive interval advances only while the control is mounted.",
          i18n"SSR states" -> i18n"The server can expose every slide or only the active one."
        )

        componentShowcase(
          i18n"Autoplay carousel",
          i18n"Previous, Next and every indicator remain explicit actions while the timer is active."
        ) {
          vbox {
            style { gap = "16px" }

            val carouselControl = carousel[SlideCard] {
              Carousel.items = slides
              Carousel.autoAdvanceMs = 2600
              Carousel.sidePreviewCount = 2
              Carousel.ssrShowAllStates = true
              Carousel.slideRenderer = (slide: SlideCard, index: Int) => renderSlide(slide, index)
            }

            hbox {
              classes = Seq("showcase-action-row", "carousel-preview-controls")
              div {
                classes = Seq("carousel-preview-controls__label")
                text(i18n"Previews per side") {}
              }
              Seq(1, 2, 3).foreach { count =>
                button(count.toString) {
                  classes = Seq("carousel-preview-choice")
                  ariaPressed = carouselControl.sidePreviewCountProperty.map(_ == count)
                  onClick(_ => carouselControl.setSidePreviewCount(count))
                }
              }
            }

            hbox {
              classes = Seq("showcase-action-row")

              button(i18n"Previous") {
                onClick(_ => carouselControl.previous())
              }

              button(i18n"Next") {
                onClick(_ => carouselControl.next())
              }

              button(i18n"Fast autoplay") {
                onClick(_ => carouselControl.autoAdvanceMsProperty.set(1400))
              }

              button(i18n"Slow autoplay") {
                onClick(_ => carouselControl.autoAdvanceMsProperty.set(3400))
              }

              button(i18n"Stop timer") {
                onClick(_ => carouselControl.autoAdvanceMsProperty.set(0))
              }
            }

            div {
              classes = Seq("showcase-result")
              text(
                carouselControl.activeIndexProperty.flatMap { index =>
                  carouselControl.autoAdvanceMsProperty.map { milliseconds =>
                    s"Active slide: ${index + 1} / ${slides.length} | autoAdvanceMs = $milliseconds"
                  }
                }
              ) {}
            }
          }
        }

        apiSection(
          i18n"Carousel DSL",
          i18n"The contextual renderer owns only the content of one slide."
        ) {
          codeBlock(
            "scala",
            """|carousel[SlideCard] {
               |  items = slides
               |  autoAdvanceMs = 2600
               |  sidePreviewCount = 2
               |  ssrShowAllStates = true
               |  slideRenderer = (slide: SlideCard, index: Int) =>
               |    renderSlide(slide, index)
               |}""".stripMargin
          )
        }

        insightGrid(
          (
            i18n"Loop",
            i18n"No dead right edge",
            i18n"next(), previous() and indicators share the same normalized active index."
          ),
          (
            i18n"Timer",
            i18n"Autoplay remains disposable",
            i18n"Changing the interval replaces the timer; unmounting always clears it."
          ),
          (
            i18n"Hydration",
            i18n"Both modes keep one tree shape",
            i18n"Active-only mode uses a dynamic mount point on the server and in the browser."
          )
        )
      }
    }
  }

  private def renderSlide(slide: SlideCard, index: Int)(using
      AbstractComponent,
      Cursor
  ): Unit =
    vbox {
      classes = Seq("carousel-demo-slide")
      style {
        minHeight = "320px"
        padding = "28px"
        boxSizing = "border-box"
      }

      div {
        classes = Seq("carousel-demo-slide__kicker")
        text(slide.kicker) {}
      }

      div {
        classes = Seq("carousel-demo-slide__title")
        text(s"${index + 1}. ${slide.title}") {}
      }

      div {
        classes = Seq("carousel-demo-slide__copy")
        text(slide.copy) {}
      }

      div {
        classes = Seq("carousel-demo-slide__footer")

        div {
          classes = Seq("carousel-demo-slide__pill")
          text("State") {}
        }

        div {
          classes = Seq("carousel-demo-slide__accent")
          text("Looping sequence") {}
        }
      }
    }
}
