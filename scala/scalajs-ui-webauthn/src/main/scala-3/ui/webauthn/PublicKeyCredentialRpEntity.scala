package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait PublicKeyCredentialRpEntity extends js.Object {
  val id: js.UndefOr[String] = js.native
  val name: String           = js.native
  @deprecated("WebAuthn no longer defines RP icons", "1.0.0")
  val icon: js.UndefOr[String] = js.native
}

object PublicKeyCredentialRpEntity {
  def apply(
      name: String,
      id: Option[String] = None,
      icon: Option[String] = None
  ): PublicKeyCredentialRpEntity = {
    val result = js.Dynamic.literal(name = name)
    id.foreach(result.updateDynamic("id")(_))
    icon.foreach(result.updateDynamic("icon")(_))
    result.asInstanceOf[PublicKeyCredentialRpEntity]
  }
}
