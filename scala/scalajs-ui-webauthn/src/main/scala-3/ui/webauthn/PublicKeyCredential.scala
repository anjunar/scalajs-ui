package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait PublicKeyCredential extends js.Object {
  val id: String                             = js.native
  val rawId: ArrayBuffer                     = js.native
  val response: AuthenticatorResponse        = js.native
  val authenticatorAttachment: String | Null = js.native
  @JSName("type")
  val credentialType: String                      = js.native
  def getClientExtensionResults(): js.Object      = js.native
  val toJSON: js.UndefOr[js.Function0[js.Object]] = js.native
}
