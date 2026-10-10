package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer
final case class UnknownCredentialOptions(rpId: String, credentialId: String) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(rpId = rpId, credentialId = credentialId)
}
