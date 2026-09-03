/**
 * Single import point for the bridge contract.
 *
 * `@void/protocol` (owned by the **ui** agent) is the real home of these types,
 * of `installVoidShim()` and of `createFakeVoid()`. While that package is still
 * being written, this module re-exports the local transcription in
 * `src/local/protocol.ts` + `src/local/fake-void.ts`, both coded straight off
 * `pvp/schema/*.json` with the same export names.
 *
 * CONSOLIDATION: when `@void/protocol` exposes an entry point, replace the two
 * `export *` lines below with
 *
 *     export * from '@void/protocol';
 *
 * and delete `src/local/protocol.ts` + `src/local/fake-void.ts`. Nothing else in
 * the app imports either file directly — every module imports from here.
 */

export * from '@/local/protocol';
export * from '@/local/fake-void';
