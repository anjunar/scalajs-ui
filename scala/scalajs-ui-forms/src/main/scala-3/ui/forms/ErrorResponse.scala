package ui.forms

final case class ErrorResponse(message: String = "", path: Seq[String] = Seq.empty) {
  def withoutHead: ErrorResponse = copy(path = path.drop(1))
}

object ErrorResponse {

  /** Hands `responses` to the control their path led to. An exhausted path addresses the control
    * itself; a remaining path continues into the control when it is a form controller, and
    * otherwise still lands on the control, the closest thing to its address.
    */
  private[forms] def deliver(control: Control[?], responses: Seq[ErrorResponse]): Unit =
    control match {
      case nested: FormController =>
        val (own, remaining) = responses.partition(_.path.isEmpty)
        if (own.nonEmpty) control.setErrors(own.map(_.message))
        if (remaining.nonEmpty) nested.setErrorResponses(remaining)
      case _ =>
        control.setErrors(responses.map(_.message))
    }
}
