/**
 * Single import point for the bridge contract.
 *
 * `@void/protocol` is the TypeScript face of `pvp/schema/*.json` — the generated
 * types, `installVoidShim()` (the reference implementation of the `void-shim.js`
 * the mod ships in the JAR), `createFakeVoid()` for the `?debug` harness, and the
 * closed registry of the 12 mods.
 *
 * Everything in this bundle imports the contract through here, so there is one
 * place to look when the schema moves.
 */

export * from '@void/protocol';
