package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class EmailConstraint(message: String = "")
    extends StaticAnnotation
