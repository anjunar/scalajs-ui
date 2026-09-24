package ui.core.dsl

import ui.core.component.AbstractComponent
import ui.core.state.ReadOnlyProperty

trait AttributeDsl {

  def setAttribute(name: String, value: String): Unit

  def removeAttribute(name: String): Unit

  def attribute(name: String): Option[String]

}

object AttributeDsl {

  private def get(name: String)(using component: AttributeDsl): String =
    component.attribute(name).getOrElse("")

  private def flag(name: String)(using component: AttributeDsl): Boolean =
    component.attribute(name).contains("true")

  private def put(name: String, value: String)(using component: AttributeDsl): Unit =
    component.setAttribute(name, value)

  private def bind(name: String, value: ReadOnlyProperty[String])(using component: AbstractComponent): Unit =
    component.addDisposable(value.observe(component.setAttribute(name, _)))

  def id(using AttributeDsl): String = get("id")
  def id_=(value: String)(using AttributeDsl): Unit = put("id", value)
  def role(using AttributeDsl): String = get("role")
  def role_=(value: String)(using AttributeDsl): Unit = put("role", value)
  def lang(using AttributeDsl): String = get("lang")
  def lang_=(value: String)(using AttributeDsl): Unit = put("lang", value)
  def title(using AttributeDsl): String = get("title")
  def title_=(value: String)(using AttributeDsl): Unit = put("title", value)
  def placeholder(using AttributeDsl): String = get("placeholder")
  def placeholder_=(value: String)(using AttributeDsl): Unit = put("placeholder", value)
  def inputMode(using AttributeDsl): String = get("inputmode")
  def inputMode_=(value: String)(using AttributeDsl): Unit = put("inputmode", value)
  def autoComplete(using AttributeDsl): String = get("autocomplete")
  def autoComplete_=(value: String)(using AttributeDsl): Unit = put("autocomplete", value)
  def spellCheck(using AttributeDsl): Boolean = flag("spellcheck")
  def spellCheck_=(value: Boolean)(using AttributeDsl): Unit = put("spellcheck", value.toString)
  def tabIndex(using AttributeDsl): Int = get("tabindex").toIntOption.getOrElse(0)
  def tabIndex_=(value: Int)(using AttributeDsl): Unit = put("tabindex", value.toString)
  def dateTime(using AttributeDsl): String = get("datetime")
  def dateTime_=(value: String)(using AttributeDsl): Unit = put("datetime", value)
  def ariaLabel(using AttributeDsl): String = get("aria-label")
  def ariaLabel_=(value: String)(using AttributeDsl): Unit = put("aria-label", value)
  def ariaLabel_=(value: ReadOnlyProperty[String])(using AbstractComponent): Unit = bind("aria-label", value)
  def ariaLabelledBy(using AttributeDsl): String = get("aria-labelledby")
  def ariaLabelledBy_=(value: String)(using AttributeDsl): Unit = put("aria-labelledby", value)
  def ariaControls(using AttributeDsl): String = get("aria-controls")
  def ariaControls_=(value: String)(using AttributeDsl): Unit = put("aria-controls", value)
  def ariaHidden(using AttributeDsl): Boolean = flag("aria-hidden")
  def ariaHidden_=(value: Boolean)(using AttributeDsl): Unit = put("aria-hidden", value.toString)
  def ariaExpanded(using AttributeDsl): Boolean = flag("aria-expanded")
  def ariaExpanded_=(value: Boolean)(using AttributeDsl): Unit = put("aria-expanded", value.toString)
  def ariaExpanded_=(value: ReadOnlyProperty[Boolean])(using component: AbstractComponent): Unit =
    component.addDisposable(value.observe(next => component.setAttribute("aria-expanded", next.toString)))
  def ariaPressed(using AttributeDsl): Boolean = flag("aria-pressed")
  def ariaPressed_=(value: Boolean)(using AttributeDsl): Unit = put("aria-pressed", value.toString)
  def ariaPressed_=(value: ReadOnlyProperty[Boolean])(using component: AbstractComponent): Unit =
    component.addDisposable(value.observe(next => component.setAttribute("aria-pressed", next.toString)))

  def setAttribute(name: String, value: String)(using component: AttributeDsl): Unit =
    component.setAttribute(name, value)

  def removeAttribute(name: String)(using component: AttributeDsl): Unit =
    component.removeAttribute(name)

  def attribute(name: String)(using component: AttributeDsl): Option[String] =
    component.attribute(name)

}
