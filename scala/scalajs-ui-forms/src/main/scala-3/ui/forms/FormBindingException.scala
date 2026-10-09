package ui.forms

import org.scalajs.dom

import scala.scalajs.LinkingInfo

/** A control could not be bound to a model property. */
final class FormBindingException(message: String) extends IllegalStateException(message)
