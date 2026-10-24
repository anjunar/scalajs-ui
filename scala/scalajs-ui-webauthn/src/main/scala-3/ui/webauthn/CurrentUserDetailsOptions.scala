package ui.webauthn

import scala.scalajs.js
final case class CurrentUserDetailsOptions(
    rpId: String,
    userId: String,
    name: String,
    displayName: String
) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(rpId = rpId, userId = userId, name = name, displayName = displayName)
}
