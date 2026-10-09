package ui.webauthn

import scala.scalajs.js

final case class RegistrationCredential(
    id: String,
    rawId: String,
    response: RegistrationResponse,
    authenticatorAttachment: Option[String] = None,
    clientExtensionResults: js.Object = js.Dynamic.literal(),
    credentialType: String = CredentialType.PublicKey
) extends WebAuthnCredentialPayload {
  def toJsObject: js.Object = {
    val result = js.Dynamic.literal(
      id = id,
      rawId = rawId,
      response = response.toJsObject,
      clientExtensionResults = clientExtensionResults
    )
    result.updateDynamic("type")(credentialType)
    authenticatorAttachment.foreach(result.updateDynamic("authenticatorAttachment")(_))
    result
  }
}

