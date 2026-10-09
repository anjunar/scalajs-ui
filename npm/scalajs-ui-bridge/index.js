import { installRuntime } from "@anjunar/scalajs-ui-core";
import { installCoreRuntime } from "./dist/fullopt/core.js";
import { installRouterRuntime } from "./dist/fullopt/router.js";
import { installControlsRuntime } from "./dist/fullopt/controls.js";
import { installViewportRuntime } from "./dist/fullopt/viewport.js";
import {
  installFormsRuntime,
  parseInstant,
  parseLocalDate,
  parseLocalDateTime,
} from "./dist/fullopt/forms.js";
import { installEditorRuntime } from "./dist/fullopt/editor.js";
import { bridgeRuntime } from "./dist/fullopt/main.js";

// "Install everything" -- each installXRuntime() is independently reachable (BridgeRuntime.scala
// gives each its own moduleID), so composing all six here, rather than inside bridgeRuntime's own
// construction, is what lets a consumer import a single feature subpath (./core, ./controls, ...)
// without pulling in the other five. bridgeRuntime's identity is unaffected either way: it is
// always this one UiRuntimeBridge instance, however it was reached.
installCoreRuntime();
installRouterRuntime();
installControlsRuntime();
installViewportRuntime();
installFormsRuntime();
installEditorRuntime();

// Register through core's shared slot, including its duplicate-runtime guard.
installRuntime(bridgeRuntime);

export { bridgeRuntime, parseInstant, parseLocalDate, parseLocalDateTime };
