import { installRuntime } from "@anjunar/scalajs-ui-core";
import { installCoreRuntime } from "./dist/fullopt/core.js";
import { bridgeRuntime } from "./dist/fullopt/main.js";

installCoreRuntime();
// Register through core's shared slot, including its duplicate-runtime guard. Safe to call
// alongside the other per-feature subpaths (./router, ./controls, ...): they all resolve to this
// same bridgeRuntime instance, so repeated installRuntime calls are a no-op, not a conflict.
installRuntime(bridgeRuntime);

export { bridgeRuntime };
