package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class DecimalMax(value: String, inclusive: Boolean = true, message: String = "")
    extends StaticAnnotation
