package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class Digits(integer: Int = 0, fraction: Int = 0, message: String = "")
    extends StaticAnnotation
