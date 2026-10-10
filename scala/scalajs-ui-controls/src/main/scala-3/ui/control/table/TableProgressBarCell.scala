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
