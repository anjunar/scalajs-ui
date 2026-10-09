package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class PositiveOrZero(message: String = "")
    extends StaticAnnotation
