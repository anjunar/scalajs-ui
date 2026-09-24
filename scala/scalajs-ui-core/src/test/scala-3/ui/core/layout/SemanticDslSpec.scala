package ui.core.layout

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.AttributeDsl
import ui.core.dsl.DslLayer.render
import ui.core.layout.FigCaption.figcaption
import ui.core.layout.Figure.figure
import ui.core.layout.Footer.footer
import ui.core.layout.Header.header
import ui.core.layout.Image.image
import ui.core.layout.Li.li
import ui.core.layout.Main.main
import ui.core.layout.Nav.nav
import ui.core.layout.Section.section
import ui.core.layout.Ul.ul
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}

class SemanticDslSpec extends AnyFlatSpec with Matchers {
  "Semantic layout DSL" should "compose typed landmarks, lists and media in SSR" in {
    val cursor = new SsrCursor()
    val root   = Runtime.mount(
      new AbstractComponent {
        val tagName = "div"

        override def compose(cursor: Cursor): Unit =
          render(this, cursor) {
            header {
              nav {
                AttributeDsl.ariaLabel_=("Primary")
                ul {
                  li { text("Home") {} }
                }
              }
            }
            main {
              section {
                figure {
                  image {
                    Image.src_=("/cover.jpg")
                    Image.alt_=("Cover")
                    Image.intrinsicWidth_=(354)
                    Image.intrinsicHeight_=(354)
                    Image.decoding_=("async")
                  }
                  figcaption { text("Cover art") {} }
                }
              }
              Iframe.iframe {
                Iframe.src_=("https://example.com/player")
                AttributeDsl.title_=("Player")
                Iframe.allowFullscreen_=(true)
              }
            }
            footer { text("End") {} }
          }
      },
      cursor
    )

    val html = cursor.collectHtml()
    html should include("<header><nav aria-label=\"Primary\"><ul><li>Home</li></ul></nav></header>")
    html should include("<main><section><figure><img src=\"/cover.jpg\" alt=\"Cover\" width=\"354\" height=\"354\" decoding=\"async\">")
    html should include("<figcaption>Cover art</figcaption></figure></section>")
    html should include("<iframe src=\"https://example.com/player\" title=\"Player\" allowfullscreen=\"true\"></iframe>")
    html should endWith("<footer>End</footer></div>")

    Runtime.unmount(root)
  }
}
