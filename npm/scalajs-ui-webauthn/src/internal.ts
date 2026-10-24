/**
 * Shared plumbing for the WebAuthn facades and codecs.
 *
 * `@anjunar/scalajs-ui-core` exports the same `defined()`, but this package deliberately has no
 * dependency on it -- it is the browser WebAuthn API plus JSON codecs, usable without the UI
 * runtime -- so the helper is kept local rather than buying a dependency edge for five lines.
 */

/** Drops `undefined` entries, so an omitted optional field stays omitted in the built record. */
export function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}
