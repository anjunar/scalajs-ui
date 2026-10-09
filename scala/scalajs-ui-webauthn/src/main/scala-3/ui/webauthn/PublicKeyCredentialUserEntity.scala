package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait PublicKeyCredentialUserEntity extends js.Object {
  val id: ArrayBuffer     = js.native
  val name: String        = js.native
  val displayName: String = js.native
  @deprecated("WebAuthn no longer defines user icons", "1.0.0")
  val icon: js.UndefOr[String] = js.native
}

object PublicKeyCredentialUserEntity {
  def apply(
      id: ArrayBuffer,
      name: String,
      displayName: String,
      icon: Option[String] = None
  ): PublicKeyCredentialUserEntity = {
    val result = js.Dynamic.literal(id = id, name = name, displayName = displayName)
    icon.foreach(result.updateDynamic("icon")(_))
    result.asInstanceOf[PublicKeyCredentialUserEntity]
  }
}
