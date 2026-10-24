package ui.webauthn

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait AuthenticatorResponse extends js.Object {
  val clientDataJSON: ArrayBuffer = js.native
}
