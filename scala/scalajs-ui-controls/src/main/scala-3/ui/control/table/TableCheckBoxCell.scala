package ui.control.table

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.on
import ui.core.render.{Cursor, DomHostElement}

import scala.scalajs.js

/** A Boolean cell that commits each user toggle atomically through the table edit model. */
final class TableCheckBoxCell[S] extends TableCell[S, Boolean] {

  override private[table] def supportsIntegratedEditor: Boolean = true

  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    DslLayer.child(new TableCheckBoxEditor(this)) {}
}
