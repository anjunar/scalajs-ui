package ui.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
final case class CredentialRequestSettings(
    signal: Option[AbortSignal] = None,
    mediation: Option[String] = None
)
