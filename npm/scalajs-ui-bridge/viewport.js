import { installRuntime } from "@anjunar/scalajs-ui-core";
import { installViewportRuntime } from "./dist/fullopt/viewport.js";
import { bridgeRuntime } from "./dist/fullopt/main.js";

installViewportRuntime();
installRuntime(bridgeRuntime);

export { bridgeRuntime };
