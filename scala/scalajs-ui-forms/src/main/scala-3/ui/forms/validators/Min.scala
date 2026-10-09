package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class Min(value: Long, message: String = "") extends StaticAnnotation
