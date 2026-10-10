package ui.webauthn

import scala.scalajs.js

final case class RegistrationResponse(
    clientDataJSON: String,
    attestationObject: String,
    transports: Seq[String] = Seq.empty,
    authenticatorData: Option[String] = None,
    publicKey: Option[String] = None,
    publicKeyAlgorithm: Option[Int] = None
) {
  def toJsObject: js.Object = {
    val result = js.Dynamic.literal(
      clientDataJSON = clientDataJSON,
      attestationObject = attestationObject,
      transports = js.Array(transports*)
    )
    authenticatorData.foreach(result.updateDynamic("authenticatorData")(_))
    publicKey.foreach(result.updateDynamic("publicKey")(_))
    publicKeyAlgorithm.foreach(result.updateDynamic("publicKeyAlgorithm")(_))
    result
  }
}
