package ui.webauthn

import scala.scalajs.js
final case class AllAcceptedCredentialsOptions(
    rpId: String,
    userId: String,
    allAcceptedCredentialIds: Seq[String]
) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(
      rpId = rpId,
      userId = userId,
      allAcceptedCredentialIds = js.Array(allAcceptedCredentialIds*)
    )
}
