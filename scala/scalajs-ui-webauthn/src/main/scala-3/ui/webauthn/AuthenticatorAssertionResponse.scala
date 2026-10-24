package ui.webauthn

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait AuthenticatorAssertionResponse extends AuthenticatorResponse {
  val authenticatorData: ArrayBuffer = js.native
  val signature: ArrayBuffer         = js.native
  val userHandle: ArrayBuffer | Null = js.native
}
