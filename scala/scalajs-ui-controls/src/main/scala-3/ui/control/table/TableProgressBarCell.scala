package ui.control.table

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.StyleDsl.*
import ui.core.render.Cursor

/** A read-only progress cell. Values from 0 to 1 are determinate; all other values are clamped. */
final class TableProgressBarCell[S] extends TableCell[S, Double] {
  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    DslLayer.child(new TableProgressBar(this)) {}
}

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

private final class TableProgressBarTrack(progress: ui.core.state.ReadOnlyProperty[Double])
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-progress-bar-cell__track")
    DslLayer.child(new TableProgressBarFill(progress)) {}
  }
}

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
