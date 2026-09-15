import { installRuntime } from "@anjunar/scalajs-ui-core";
import { installControlsRuntime } from "./dist/fullopt/controls.js";
import { bridgeRuntime } from "./dist/fullopt/main.js";

installControlsRuntime();
installRuntime(bridgeRuntime);

export { bridgeRuntime };
