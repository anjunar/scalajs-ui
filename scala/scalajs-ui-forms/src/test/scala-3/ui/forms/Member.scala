package ui.forms

import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.ClassDsl.{classIf, classes}
import ui.core.dsl.DslLayer.render
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.Property
import ui.core.state.ListProperty
import ui.control.table.TableColumn.*
import ui.control.table.TableView.tableView
import ui.control.table.forms.TableComboBoxCell.comboBoxCell
import ui.forms.ComboBox.*
import ui.forms.Form.form
import ui.viewport.Viewport
import ui.viewport.Viewport.viewport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private final case class Member(id: Int, name: String)
