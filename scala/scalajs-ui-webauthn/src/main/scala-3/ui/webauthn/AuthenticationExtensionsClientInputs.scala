package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

/** Extension inputs are intentionally open because WebAuthn extensions are registry based. */
type AuthenticationExtensionsClientInputs = js.Object
