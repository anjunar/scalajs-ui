package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait PublicKeyCredentialDescriptor extends js.Object {
  @JSName("type")
  val credentialType: String                   = js.native
  val id: ArrayBuffer                          = js.native
  val transports: js.UndefOr[js.Array[String]] = js.native
}

object PublicKeyCredentialDescriptor {
  def apply(
      id: ArrayBuffer,
      credentialType: String = CredentialType.PublicKey,
      transports: Seq[String] = Seq.empty
  ): PublicKeyCredentialDescriptor = {
    val result = js.Dynamic.literal(id = id)
    result.updateDynamic("type")(credentialType)
    if (transports.nonEmpty) result.updateDynamic("transports")(js.Array(transports*))
    result.asInstanceOf[PublicKeyCredentialDescriptor]
  }
}
