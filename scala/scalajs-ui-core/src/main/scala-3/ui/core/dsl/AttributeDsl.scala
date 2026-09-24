package ui.core.dsl

import ui.core.component.AbstractComponent
import ui.core.state.ReadOnlyProperty

trait AttributeDsl {

  def setAttribute(name: String, value: String): Unit

  def removeAttribute(name: String): Unit

  def attribute(name: String): Option[String]

}

object AttributeDsl {

  private def put(name: String, value: String)(using component: AttributeDsl): Unit =
    component.setAttribute(name, value)

  private def bind(name: String, value: ReadOnlyProperty[String])(using component: AbstractComponent): Unit =
    component.addDisposable(value.observe(component.setAttribute(name, _)))

  def id_=(value: String)(using AttributeDsl): Unit = put("id", value)
  def role_=(value: String)(using AttributeDsl): Unit = put("role", value)
  def lang_=(value: String)(using AttributeDsl): Unit = put("lang", value)
  def title_=(value: String)(using AttributeDsl): Unit = put("title", value)
  def placeholder_=(value: String)(using AttributeDsl): Unit = put("placeholder", value)
  def inputMode_=(value: String)(using AttributeDsl): Unit = put("inputmode", value)
  def autoComplete_=(value: String)(using AttributeDsl): Unit = put("autocomplete", value)
  def spellCheck_=(value: Boolean)(using AttributeDsl): Unit = put("spellcheck", value.toString)
  def tabIndex_=(value: Int)(using AttributeDsl): Unit = put("tabindex", value.toString)
  def dateTime_=(value: String)(using AttributeDsl): Unit = put("datetime", value)
  def ariaLabel_=(value: String)(using AttributeDsl): Unit = put("aria-label", value)
  def ariaLabel_=(value: ReadOnlyProperty[String])(using AbstractComponent): Unit = bind("aria-label", value)
  def ariaLabelledBy_=(value: String)(using AttributeDsl): Unit = put("aria-labelledby", value)
  def ariaControls_=(value: String)(using AttributeDsl): Unit = put("aria-controls", value)
  def ariaHidden_=(value: Boolean)(using AttributeDsl): Unit = put("aria-hidden", value.toString)
  def ariaExpanded_=(value: Boolean)(using AttributeDsl): Unit = put("aria-expanded", value.toString)
  def ariaPressed_=(value: Boolean)(using AttributeDsl): Unit = put("aria-pressed", value.toString)

  def setAttribute(name: String, value: String)(using component: AttributeDsl): Unit =
    component.setAttribute(name, value)

  def removeAttribute(name: String)(using component: AttributeDsl): Unit =
    component.removeAttribute(name)

  def attribute(name: String)(using component: AttributeDsl): Option[String] =
    component.attribute(name)

}
