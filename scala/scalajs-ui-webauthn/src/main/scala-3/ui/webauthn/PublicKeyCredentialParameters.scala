package ui.webauthn

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

@js.native
trait PublicKeyCredentialParameters extends js.Object {
  @JSName("type")
  val credentialType: String = js.native
  val alg: Int               = js.native
}

object PublicKeyCredentialParameters {
  def apply(
      alg: Int,
      credentialType: String = CredentialType.PublicKey
  ): PublicKeyCredentialParameters = {
    val result = js.Dynamic.literal(alg = alg)
    result.updateDynamic("type")(credentialType)
    result.asInstanceOf[PublicKeyCredentialParameters]
  }
}
