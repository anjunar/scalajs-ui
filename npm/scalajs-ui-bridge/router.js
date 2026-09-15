import { installRuntime } from "@anjunar/scalajs-ui-core";
import { installRouterRuntime } from "./dist/fullopt/router.js";
import { bridgeRuntime } from "./dist/fullopt/main.js";

installRouterRuntime();
installRuntime(bridgeRuntime);

export { bridgeRuntime };
