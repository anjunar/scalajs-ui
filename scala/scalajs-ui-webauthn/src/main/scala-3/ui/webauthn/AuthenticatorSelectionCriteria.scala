package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait AuthenticatorSelectionCriteria extends js.Object {
  val authenticatorAttachment: js.UndefOr[String] = js.native
  val residentKey: js.UndefOr[String]             = js.native
  val requireResidentKey: js.UndefOr[Boolean]     = js.native
  val userVerification: js.UndefOr[String]        = js.native
}

object AuthenticatorSelectionCriteria {
  def apply(
      authenticatorAttachment: Option[String] = None,
      residentKey: Option[String] = None,
      requireResidentKey: Option[Boolean] = None,
      userVerification: Option[String] = None
  ): AuthenticatorSelectionCriteria = {
    val result = js.Dynamic.literal()
    authenticatorAttachment.foreach(result.updateDynamic("authenticatorAttachment")(_))
    residentKey.foreach(result.updateDynamic("residentKey")(_))
    requireResidentKey.foreach(result.updateDynamic("requireResidentKey")(_))
    userVerification.foreach(result.updateDynamic("userVerification")(_))
    result.asInstanceOf[AuthenticatorSelectionCriteria]
  }
}
