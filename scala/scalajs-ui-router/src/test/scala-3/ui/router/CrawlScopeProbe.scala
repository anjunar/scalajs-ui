package ui.router

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.context.CrawlScope
import ui.core.dsl.DslLayer
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** Renders the CrawlScope path provided by the router as text. */
private final class CrawlScopeProbe extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      text(s"crawl-path=${CrawlScope.path(using this)}") {}
    }
}
