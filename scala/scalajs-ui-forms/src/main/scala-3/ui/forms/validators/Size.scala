package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class Size(min: Int = 0, max: Int = Int.MaxValue, message: String = "")
    extends StaticAnnotation
