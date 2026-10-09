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

private final class TestIntervalScheduler extends IntervalScheduler {
  private final class Task(val action: () => Unit) {
    var active: Boolean = true
  }

  private val tasks      = mutable.ArrayBuffer.empty[Task]
  val scheduledIntervals = mutable.ArrayBuffer.empty[Int]
  var disposedCount      = 0

  override def schedule(intervalMs: Int)(action: () => Unit): Disposable = {
    val task = new Task(action)
    scheduledIntervals += intervalMs
    tasks += task
    Disposable {
      if (task.active) {
        task.active = false
        disposedCount += 1
      }
    }
  }

  def tick(): Unit = tasks.reverseIterator.find(_.active).foreach(_.action())

  def activeTaskCount: Int = tasks.count(_.active)
}

