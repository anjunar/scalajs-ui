package ui.control.table

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.StyleDsl.*
import ui.core.render.Cursor
private final class TableProgressBarFill(progress: ui.core.state.ReadOnlyProperty[Double])
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-progress-bar-cell__fill")
    style {
      width = progress.map(value => s"${value * 100}%")
    }
  }
}
