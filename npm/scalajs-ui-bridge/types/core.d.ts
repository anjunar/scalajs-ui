/**
 * Installs only the core registrations (vbox, hbox, button, drawer*, i18n-provider) instead of
 * every feature `@anjunar/scalajs-ui-bridge`'s bare import installs -- see BridgeRuntime.scala for
 * why this is a real, separately-downloadable subset rather than a re-export label.
 */
export { bridgeRuntime } from "./index.js";
