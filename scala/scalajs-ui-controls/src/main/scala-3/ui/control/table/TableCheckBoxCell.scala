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

private final class TableCheckBoxEditor[S](cell: TableCheckBoxCell[S]) extends AbstractComponent {
  override val tagName: String = "input"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-check-box-cell__editor")
    setAttribute("type", "checkbox")
    Option(cell.tableColumn).foreach(column => setAttribute("aria-label", column.text))

    def writable: Boolean = Option(cell.tableView).exists { table =>
      table.editableProperty.get && Option(cell.tableColumn).exists(table.isColumnEditable) &&
      cell.editableProperty.get
    }
    def syncDisabled(): Unit = setProperty("disabled", !writable)

    addDisposable(
      cell.itemProperty.observe(value => setProperty("checked", Option(value).contains(true)))
    )
    addDisposable(cell.editableProperty.observeWithoutInitial(_ => syncDisabled()))
    Option(cell.tableView).foreach { table =>
      addDisposable(table.editableProperty.observeWithoutInitial(_ => syncDisabled()))
      addDisposable(
        table.columnEditabilityRevisionProperty.observeWithoutInitial(_ => syncDisabled())
      )
    }
    syncDisabled()

    on("change") { _ => commitToggle(nativeChecked) }
    on("keydown") { event =>
      event.raw match {
        case key: dom.KeyboardEvent if !key.defaultPrevented && !key.isComposing =>
          key.key match {
            case "Enter" =>
              key.preventDefault(); key.stopPropagation()
              commitToggle(!nativeChecked)
            case "Escape" if cell.editing =>
              key.preventDefault(); key.stopPropagation()
              Option(cell.tableView).foreach { table =>
                if (table.cancelEdit()) {
                  setProperty("checked", Option(cell.itemProperty.get).contains(true))
                  table.focusGridAfterEditor()
                }
              }
            case "Tab" if cell.editing =>
              key.preventDefault(); key.stopPropagation()
              val row    = cell.indexProperty.get
              val column = cell.tableColumn
              Option(cell.tableView).foreach { table =>
                if (table.commitEdit(nativeChecked))
                  Option(column).fold(table.focusGridAfterEditor())(
                    table.moveFocusAfterEdit(row, _, key.shiftKey)
                  )
              }
            case _ => ()
          }
        case _ => ()
      }
    }
    addDisposable(cell.editingProperty.observeWithoutInitial { editing =>
      if (editing && cursor.isBrowser)
        host.asInstanceOf[DomHostElement].node match {
          case input: dom.HTMLInputElement =>
            input.focus(js.Dynamic.literal(preventScroll = true).asInstanceOf[dom.FocusOptions])
          case _ => ()
        }
    })
  }

  private def commitToggle(value: Boolean): Unit =
    Option(cell.tableView).foreach { table =>
      Option(cell.tableColumn).foreach { column =>
        table.focusModel.focus(cell.indexProperty.get, column)
        val started = cell.editing || cell.startIntegratedEdit()
        if (started) table.commitEdit(value)
        setProperty("checked", Option(cell.itemProperty.get).contains(true))
      }
    }

  private def nativeChecked: Boolean = host.property[Boolean]("checked").contains(true)
}
