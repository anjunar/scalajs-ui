package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class NegativeOrZero(message: String = "")
    extends StaticAnnotation
