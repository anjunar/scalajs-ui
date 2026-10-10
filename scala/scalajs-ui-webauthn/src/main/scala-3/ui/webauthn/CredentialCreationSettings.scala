package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer
final case class CredentialCreationSettings(
    signal: Option[AbortSignal] = None,
    mediation: Option[String] = None
)
