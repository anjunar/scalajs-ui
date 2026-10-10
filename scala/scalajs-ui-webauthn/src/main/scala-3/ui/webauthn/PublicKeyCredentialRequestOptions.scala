package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait PublicKeyCredentialRequestOptions extends js.Object {
  val challenge: ArrayBuffer                                                = js.native
  val timeout: js.UndefOr[Double]                                           = js.native
  val rpId: js.UndefOr[String]                                              = js.native
  val allowCredentials: js.UndefOr[js.Array[PublicKeyCredentialDescriptor]] = js.native
  val userVerification: js.UndefOr[String]                                  = js.native
  val hints: js.UndefOr[js.Array[String]]                                   = js.native
  val extensions: js.UndefOr[AuthenticationExtensionsClientInputs]          = js.native
}

object PublicKeyCredentialRequestOptions {
  def apply(
      challenge: ArrayBuffer,
      timeout: Option[Double] = None,
      rpId: Option[String] = None,
      allowCredentials: Seq[PublicKeyCredentialDescriptor] = Seq.empty,
      userVerification: Option[String] = None,
      hints: Seq[String] = Seq.empty,
      extensions: Option[AuthenticationExtensionsClientInputs] = None
  ): PublicKeyCredentialRequestOptions = {
    val result = js.Dynamic.literal(challenge = challenge)
    timeout.foreach(result.updateDynamic("timeout")(_))
    rpId.foreach(result.updateDynamic("rpId")(_))
    if (allowCredentials.nonEmpty)
      result.updateDynamic("allowCredentials")(js.Array(allowCredentials*))
    userVerification.foreach(result.updateDynamic("userVerification")(_))
    if (hints.nonEmpty) result.updateDynamic("hints")(js.Array(hints*))
    extensions.foreach(result.updateDynamic("extensions")(_))
    result.asInstanceOf[PublicKeyCredentialRequestOptions]
  }
}
