package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait AuthenticatorAttestationResponse extends AuthenticatorResponse {
  val attestationObject: ArrayBuffer                              = js.native
  val getAuthenticatorData: js.UndefOr[js.Function0[ArrayBuffer]] = js.native
  val getPublicKey: js.UndefOr[js.Function0[ArrayBuffer | Null]]  = js.native
  val getPublicKeyAlgorithm: js.UndefOr[js.Function0[Int]]        = js.native
  val getTransports: js.UndefOr[js.Function0[js.Array[String]]]   = js.native
}
