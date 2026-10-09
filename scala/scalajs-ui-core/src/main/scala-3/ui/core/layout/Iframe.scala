package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

final class Iframe extends AbstractComponent {
  val tagName = "iframe"

  def src: String = attribute("src").getOrElse("")
  def src_=(value: String): Unit = setAttribute("src", value)

  def allowFullscreen: Boolean = attribute("allowfullscreen").isDefined

  def allowFullscreen_=(value: Boolean): Unit =
    if (value) setAttribute("allowfullscreen", "true")
    else removeAttribute("allowfullscreen")
}

object Iframe {
  def iframe(body: Iframe ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Iframe =
    DslLayer.child(new Iframe()) {
      body
    }

  def src(using iframe: Iframe): String = iframe.src
  def src_=(value: String)(using iframe: Iframe): Unit = iframe.src_=(value)

  def allowFullscreen(using iframe: Iframe): Boolean = iframe.allowFullscreen
  def allowFullscreen_=(value: Boolean)(using iframe: Iframe): Unit =
    iframe.allowFullscreen_=(value)
}
