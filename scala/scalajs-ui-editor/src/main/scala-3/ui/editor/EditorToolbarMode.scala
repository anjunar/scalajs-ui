package ui.editor

import ui.core.component.AbstractComponent
import ui.core.context.UrlScope

import ui.core.dsl.AttributeDsl.{ariaLabel, ariaLabel_=, setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.{addClass, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Button.{button, buttonType}
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.Condition.when
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.{Disposable, Property}
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.editor.plugins.EditorPlugin
import ui.forms.Form.FormContext
import ui.forms.{Control, Editable, Placeholder}

import org.scalajs.dom.{HTMLDivElement, HTMLElement}

import scala.collection.mutable
import scala.compiletime.uninitialized

enum EditorToolbarMode:
  case Ribbon, Menu, Floating
