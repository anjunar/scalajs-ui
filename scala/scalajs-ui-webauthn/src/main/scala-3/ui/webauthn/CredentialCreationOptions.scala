package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait CredentialCreationOptions extends js.Object {
  val publicKey: PublicKeyCredentialCreationOptions = js.native
  val signal: js.UndefOr[AbortSignal]               = js.native
  val mediation: js.UndefOr[String]                 = js.native
}

object CredentialCreationOptions {
  def withSettings(
      publicKey: PublicKeyCredentialCreationOptions,
      settings: CredentialCreationSettings = CredentialCreationSettings()
  ): CredentialCreationOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    settings.signal.foreach(result.updateDynamic("signal")(_))
    settings.mediation.foreach(result.updateDynamic("mediation")(_))
    result.asInstanceOf[CredentialCreationOptions]
  }

  def apply(
      publicKey: PublicKeyCredentialCreationOptions,
      signal: Option[js.Any] = None
  ): CredentialCreationOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    signal.foreach(result.updateDynamic("signal")(_))
    result.asInstanceOf[CredentialCreationOptions]
  }
}
