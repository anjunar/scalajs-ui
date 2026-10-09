package ui.webauthn

import scala.scalajs.js

trait WebAuthnCredentialPayload {
  def id: String
  def rawId: String
  def authenticatorAttachment: Option[String]
  def clientExtensionResults: js.Object
  def credentialType: String
  def toJsObject: js.Object

  final def toJson: String = js.JSON.stringify(toJsObject)
}

