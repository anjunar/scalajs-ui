package ui.control.table

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.ListProperty
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.core.statement.Foreach.foreachIndexed

import scala.scalajs.js

/** A compact native single-selection editor backed by a live list of choices. */
final class TableChoiceBoxCell[S, T](
    val items: ListProperty[T],
    val converter: T => String = (value: T) => Option(value).fold("")(_.toString),
    val identityBy: T => Any = (value: T) => value.asInstanceOf[Any]
) extends TableCell[S, T] {

  override private[table] def supportsIntegratedEditor: Boolean = true

  private[table] def same(left: T, right: T): Boolean =
    if (left == null || right == null) left == right
    else identityBy(left) == identityBy(right)

  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    dynamic(
      editingProperty.map(
        if (_) new TableChoiceBoxEditor(this) else new TableChoiceBoxDisplay(this)
      )
    )
}

private final class TableChoiceBoxDisplay[S, T](cell: TableChoiceBoxCell[S, T])
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-choice-box-cell__display")
    text(cell.itemProperty.map {
      case null  => ""
      case value => cell.converter(value.asInstanceOf[T])
    }) {}
  }
}

private final class TableChoiceBoxEditor[S, T](cell: TableChoiceBoxCell[S, T])
    extends AbstractComponent {
  override val tagName: String = "select"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-choice-box-cell__editor")
    Option(cell.tableColumn).foreach(column => setAttribute("aria-label", column.text))

    foreachIndexed(cell.items) { (item, index) =>
      DslLayer.child(new TableChoiceBoxOption(item, index, cell.converter)) {}
    }

    addDisposable(cell.items.observeChanges(_ => syncSelection()))
    addDisposable(cell.itemProperty.observeWithoutInitial(_ => syncSelection()))

    on("change") { _ =>
      selectedItem.foreach { value =>
        Option(cell.tableView).foreach { table =>
          if (table.commitEdit(value)) table.focusGridAfterEditor()
        }
      }
    }
    on("keydown") { event =>
      event.raw match {
        case key: dom.KeyboardEvent if !key.defaultPrevented && !key.isComposing =>
          key.key match {
            case "Enter" =>
              selectedItem.foreach { value =>
                key.preventDefault(); key.stopPropagation()
                Option(cell.tableView).foreach { table =>
                  if (table.commitEdit(value)) table.focusGridAfterEditor()
                }
              }
            case "Escape" =>
              key.preventDefault(); key.stopPropagation()
              Option(cell.tableView).foreach { table =>
                if (table.cancelEdit()) table.focusGridAfterEditor()
              }
            case "Tab" =>
              selectedItem.foreach { value =>
                key.preventDefault(); key.stopPropagation()
                val row    = cell.indexProperty.get
                val column = cell.tableColumn
                Option(cell.tableView).foreach { table =>
                  if (table.commitEdit(value))
                    Option(column).fold(table.focusGridAfterEditor())(
                      table.moveFocusAfterEdit(row, _, key.shiftKey)
                    )
                }
              }
            case _ => ()
          }
        case _ => ()
      }
    }
  }

  override def afterCompose(cursor: Cursor): Unit = {
    syncSelection()
    if (cursor.isBrowser)
      host.asInstanceOf[DomHostElement].node match {
        case select: dom.HTMLSelectElement =>
          select.focus(js.Dynamic.literal(preventScroll = true).asInstanceOf[dom.FocusOptions])
        case _ => ()
      }
  }

  private def draft: T | Null =
    Option(cell.tableView)
      .flatMap(table => Option(table.editingValueProperty.get))
      .map(_.asInstanceOf[T])
      .orElse(Option(cell.itemProperty.get))
      .orNull

  private def selectedIndex: Int =
    draft match {
      case null  => -1
      case value => cell.items.indexWhere(cell.same(_, value.asInstanceOf[T]))
    }

  private def selectedItem: Option[T] =
    cell.items.lift(nativeSelectedIndex)

  private def nativeSelectedIndex: Int =
    host.property[Double]("selectedIndex").fold(-1)(_.toInt)

  private def syncSelection(): Unit = {
    val index = selectedIndex
    setProperty("selectedIndex", index)
    setAttribute("aria-invalid", (index < 0).toString)
  }
}

private final class TableChoiceBoxOption[T](item: T, index: Int, converter: T => String)
    extends AbstractComponent {
  override val tagName: String = "option"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    setAttribute("value", index.toString)
    text(converter(item)) {}
  }
}
