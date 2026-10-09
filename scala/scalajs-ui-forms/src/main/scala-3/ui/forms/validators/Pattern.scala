package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class Pattern(regex: String, message: String = "")
    extends StaticAnnotation
