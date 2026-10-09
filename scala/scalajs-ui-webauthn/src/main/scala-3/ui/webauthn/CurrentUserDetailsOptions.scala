package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer
final case class CurrentUserDetailsOptions(
    rpId: String,
    userId: String,
    name: String,
    displayName: String
) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(rpId = rpId, userId = userId, name = name, displayName = displayName)
}
