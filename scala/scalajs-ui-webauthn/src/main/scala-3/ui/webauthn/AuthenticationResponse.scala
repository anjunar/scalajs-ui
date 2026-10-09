package ui.webauthn

import scala.scalajs.js

final case class AuthenticationResponse(
    clientDataJSON: String,
    authenticatorData: String,
    signature: String,
    userHandle: Option[String] = None
) {
  def toJsObject: js.Object = {
    val result = js.Dynamic.literal(
      clientDataJSON = clientDataJSON,
      authenticatorData = authenticatorData,
      signature = signature
    )
    userHandle.foreach(result.updateDynamic("userHandle")(_))
    result
  }
}
