/**
 * Single import point for the shared component set.
 *
 * `@void/ui` (owned by the **ui** agent) is the real home of these. While that
 * package is being written the overlay uses `src/local/ui.tsx`, whose exports
 * carry the same names and props.
 *
 * CONSOLIDATION: replace the line below with `export * from '@void/ui';` and
 * delete `src/local/ui.tsx`. No screen imports the local file directly.
 */
export * from '@/local/ui';
