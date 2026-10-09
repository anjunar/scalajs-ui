package ui.control.table

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.StyleDsl.*
import ui.core.render.Cursor
private final class TableProgressBar[S](cell: TableProgressBarCell[S]) extends AbstractComponent {
  override val tagName: String = "span"

  private val progress = cell.itemProperty.map {
    case null  => 0.0
    case value => normalized(value.asInstanceOf[Double])
  }

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-progress-bar-cell")
    setAttribute("role", "progressbar")
    setAttribute("aria-valuemin", "0")
    setAttribute("aria-valuemax", "100")
    addDisposable(
      progress.observe(value => setAttribute("aria-valuenow", math.round(value * 100).toString))
    )
    DslLayer.child(new TableProgressBarTrack(progress)) {}
  }

  private def normalized(value: Double): Double =
    if (value.isNaN) 0.0 else math.max(0.0, math.min(1.0, value))
}

