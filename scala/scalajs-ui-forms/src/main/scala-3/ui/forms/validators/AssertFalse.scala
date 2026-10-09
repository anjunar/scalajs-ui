package ui.forms.validators

import scala.annotation.StaticAnnotation

final case class AssertFalse(message: String = "")  extends StaticAnnotation
