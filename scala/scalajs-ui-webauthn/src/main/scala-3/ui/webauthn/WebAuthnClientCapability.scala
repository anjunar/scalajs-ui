package ui.webauthn
object WebAuthnClientCapability {
  final val ConditionalCreate                  = "conditionalCreate"
  final val ConditionalGet                     = "conditionalGet"
  final val HybridTransport                    = "hybridTransport"
  final val PasskeyPlatformAuthenticator       = "passkeyPlatformAuthenticator"
  final val UserVerifyingPlatformAuthenticator = "userVerifyingPlatformAuthenticator"
  final val RelatedOrigins                     = "relatedOrigins"
  final val SignalAllAcceptedCredentials       = "signalAllAcceptedCredentials"
  final val SignalCurrentUserDetails           = "signalCurrentUserDetails"
  final val SignalUnknownCredential            = "signalUnknownCredential"
}
