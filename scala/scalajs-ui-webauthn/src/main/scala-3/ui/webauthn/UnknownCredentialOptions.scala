package ui.webauthn

import scala.scalajs.js
final case class UnknownCredentialOptions(rpId: String, credentialId: String) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(rpId = rpId, credentialId = credentialId)
}
