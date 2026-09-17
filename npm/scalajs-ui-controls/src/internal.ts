/**
 * Shared plumbing for the control facades.
 *
 * Every control is a registry entry in `scalajs-ui-bridge` (`ControlFactories.scala`).
 * The consumer writes ambient-scope DSL callbacks -- `() => void`,
 * `(item, index) => void`, `(row) => void` -- and this module turns each into the
 * `(scope) => void` the bridge runs against a fresh handle, the same wrap
 * `@anjunar/scalajs-ui-router`'s `toFacadeRoute` applies to a route loader.
 */
import { withScope } from "@anjunar/scalajs-ui-core";
import type { ScopeHandle } from "@anjunar/scalajs-ui-core";

/** A body the bridge runs: it opens the ambient scope and calls the consumer's callback. */
export type ScopeBody = (scope: ScopeHandle) => void;

/** Wraps a plain `() => void` DSL body. */
export function body(run: () => void): ScopeBody {
  return (scope) => withScope(scope, null, run);
}

/** Wraps an `(item, index) => void` cell / slide renderer into `(item, index) => ScopeBody`. */
export function itemBody<T>(
  render: (item: T, index: number) => void
): (item: T, index: number) => ScopeBody {
  return (item, index) => (scope) => withScope(scope, null, () => render(item, index));
}

/** Wraps a `(row, context) => void` column cell into `(row, context) => ScopeBody`. */
export function rowBody<T, C>(
  render: (row: T, context: C) => void
): (row: T, context: C) => ScopeBody {
  return (row, context) => (scope) => withScope(scope, null, () => render(row, context));
}

/** Wraps a `(value, row, context) => void` column cell into `(value, row, context) => ScopeBody`. */
export function valueCellBody<V, T, C>(
  render: (value: V, row: T, context: C) => void
): (value: V, row: T, context: C) => ScopeBody {
  return (value, row, context) => (scope) =>
    withScope(scope, null, () => render(value, row, context));
}

/** Wraps a `(state) => void` sort indicator body into `(state) => ScopeBody`. Composed once per
 * header, like `rowBody`; `state` itself stays reactive, so the app binds to it declaratively
 * instead of being re-invoked on every sort change. */
export function stateBody<T>(render: (state: T) => void): (state: T) => ScopeBody {
  return (state) => (scope) => withScope(scope, null, () => render(state));
}

/**
 * Drops `undefined` entries.
 *
 * The bridge reads top-level control options by key presence
 * (`options.get("rowHeight")`), so an explicit `rowHeight: undefined` would be
 * seen as "set to undefined" and coerced to `NaN`. Column sub-objects use
 * `js.UndefOr` and do not need this.
 */
export function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}
