package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait PublicKeyCredentialCreationOptions extends js.Object {
  val rp: PublicKeyCredentialRpEntity                                         = js.native
  val user: PublicKeyCredentialUserEntity                                     = js.native
  val challenge: ArrayBuffer                                                  = js.native
  val pubKeyCredParams: js.Array[PublicKeyCredentialParameters]               = js.native
  val timeout: js.UndefOr[Double]                                             = js.native
  val excludeCredentials: js.UndefOr[js.Array[PublicKeyCredentialDescriptor]] = js.native
  val authenticatorSelection: js.UndefOr[AuthenticatorSelectionCriteria]      = js.native
  val hints: js.UndefOr[js.Array[String]]                                     = js.native
  val attestation: js.UndefOr[String]                                         = js.native
  val attestationFormats: js.UndefOr[js.Array[String]]                        = js.native
  val extensions: js.UndefOr[AuthenticationExtensionsClientInputs]            = js.native
}

object PublicKeyCredentialCreationOptions {
  def apply(
      rp: PublicKeyCredentialRpEntity,
      user: PublicKeyCredentialUserEntity,
      challenge: ArrayBuffer,
      pubKeyCredParams: Seq[PublicKeyCredentialParameters],
      timeout: Option[Double] = None,
      attestation: Option[String] = None,
      excludeCredentials: Seq[PublicKeyCredentialDescriptor] = Seq.empty,
      authenticatorSelection: Option[AuthenticatorSelectionCriteria] = None,
      hints: Seq[String] = Seq.empty,
      extensions: Option[AuthenticationExtensionsClientInputs] = None,
      attestationFormats: Seq[String] = Seq.empty
  ): PublicKeyCredentialCreationOptions = {
    val result = js.Dynamic.literal(
      rp = rp,
      user = user,
      challenge = challenge,
      pubKeyCredParams = js.Array(pubKeyCredParams*)
    )
    timeout.foreach(result.updateDynamic("timeout")(_))
    attestation.foreach(result.updateDynamic("attestation")(_))
    if (excludeCredentials.nonEmpty)
      result.updateDynamic("excludeCredentials")(js.Array(excludeCredentials*))
    authenticatorSelection.foreach(result.updateDynamic("authenticatorSelection")(_))
    if (hints.nonEmpty) result.updateDynamic("hints")(js.Array(hints*))
    extensions.foreach(result.updateDynamic("extensions")(_))
    if (attestationFormats.nonEmpty)
      result.updateDynamic("attestationFormats")(js.Array(attestationFormats*))
    result.asInstanceOf[PublicKeyCredentialCreationOptions]
  }
}
