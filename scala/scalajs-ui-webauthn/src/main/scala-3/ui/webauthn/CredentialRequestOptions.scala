package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait CredentialRequestOptions extends js.Object {
  val publicKey: PublicKeyCredentialRequestOptions = js.native
  val mediation: js.UndefOr[String]                = js.native
  val signal: js.UndefOr[AbortSignal]              = js.native
}

object CredentialRequestOptions {
  def withSettings(
      publicKey: PublicKeyCredentialRequestOptions,
      settings: CredentialRequestSettings = CredentialRequestSettings()
  ): CredentialRequestOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    settings.mediation.foreach(result.updateDynamic("mediation")(_))
    settings.signal.foreach(result.updateDynamic("signal")(_))
    result.asInstanceOf[CredentialRequestOptions]
  }

  def apply(
      publicKey: PublicKeyCredentialRequestOptions,
      mediation: Option[String] = None,
      signal: Option[js.Any] = None
  ): CredentialRequestOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    mediation.foreach(result.updateDynamic("mediation")(_))
    signal.foreach(result.updateDynamic("signal")(_))
    result.asInstanceOf[CredentialRequestOptions]
  }
}
