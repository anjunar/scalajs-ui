package ui.control.table.forms

import org.scalajs.dom
import ui.control.table.{TableCell, TableColumn}
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.addClass
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.on
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.ListProperty
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.forms.ComboBox
import ui.forms.ComboBox.*

import scala.scalajs.js

/** A table-managed selection editor using the Forms ComboBox and its viewport overlay. */
final class TableComboBoxCell[S, T](
    val items: ListProperty[T],
    val converter: T => String = (value: T) => Option(value).fold("")(_.toString),
    val identityBy: T => Any = (value: T) => value.asInstanceOf[Any]
) extends TableCell[S, T] {

  override private[table] def supportsIntegratedEditor: Boolean = true

  override protected def renderContent(using AbstractComponent, Cursor): Unit =
    dynamic(
      editingProperty.map(
        if (_) new TableComboBoxEditor(this) else new TableComboBoxDisplay(this)
      )
    )
}

object TableComboBoxCell {
  def comboBoxCell[S, T](
      items: ListProperty[T],
      converter: T => String = (value: T) => Option(value).fold("")(_.toString),
      identityBy: T => Any = (value: T) => value.asInstanceOf[Any]
  )(using column: TableColumn[S, T]): Unit =
    column.cellFactoryProperty.set(Some(_ => new TableComboBoxCell(items, converter, identityBy)))
}

private final class TableComboBoxDisplay[S, T](cell: TableComboBoxCell[S, T])
    extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-combo-box-cell__display")
    text(cell.itemProperty.map {
      case null  => ""
      case value => cell.converter(value.asInstanceOf[T])
    }) {}
  }
}

private final class TableComboBoxEditor[S, T](cell: TableComboBoxCell[S, T])
    extends AbstractComponent {
  override val tagName: String    = "div"
  private var editor: ComboBox[T] = null

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("ui-table-combo-box-cell__editor")
    on("keydown") { event =>
      event.raw match {
        case key: dom.KeyboardEvent if !key.defaultPrevented && !key.isComposing =>
          key.key match {
            case "Escape" =>
              key.preventDefault(); key.stopPropagation()
              Option(cell.tableView).foreach { table =>
                if (table.cancelEdit()) table.focusGridAfterEditor()
              }
            case "Tab" =>
              key.preventDefault(); key.stopPropagation()
              val row    = cell.indexProperty.get
              val column = cell.tableColumn
              Option(cell.tableView).foreach { table =>
                if (table.commitEdit(editor.valueProperty.get))
                  Option(column).fold(table.focusGridAfterEditor())(
                    table.moveFocusAfterEdit(row, _, key.shiftKey)
                  )
              }
            case _ => ()
          }
        case _ => ()
      }
    }

    editor = comboBox[T](Option(cell.tableColumn).fold("")(_.text), standalone = true) {
      ComboBox.items = cell.items.toSeq
      ComboBox.converter = cell.converter
      ComboBox.identityBy = cell.identityBy
      draft match {
        case null  => ()
        case value => ComboBox.selection.setAll(Seq(value.asInstanceOf[T]))
      }
      addDisposable(cell.items.observeChanges(_ => ComboBox.items.setAll(cell.items.toSeq)))
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) {
      addDisposable(editor.dirtyProperty.observeWithoutInitial { dirty =>
        if (dirty)
          Option(cell.tableView).foreach { table =>
            if (table.commitEdit(editor.valueProperty.get)) table.focusGridAfterEditor()
          }
      })
      editor.host.asInstanceOf[DomHostElement].node match {
        case element: dom.HTMLElement =>
          element.focus(js.Dynamic.literal(preventScroll = true).asInstanceOf[dom.FocusOptions])
          editor.toggle()
        case _ => ()
      }
    }

  private def draft: T | Null =
    Option(cell.tableView)
      .flatMap(table => Option(table.editingValueProperty.get))
      .map(_.asInstanceOf[T])
      .orElse(Option(cell.itemProperty.get))
      .orNull
}
