package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer
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
